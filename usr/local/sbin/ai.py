#!/usr/bin/env python3
import os
import re
import sys
import json
import logging
import argparse
from typing import Optional, Tuple, Union, Callable
from urllib import request, parse
from urllib.error import HTTPError
from http.client import HTTPResponse, IncompleteRead


__version__ = '0.3.0'


class Config:
    api_key = None
    api_base_url = 'https://api.openai.com/v1/'
    default_model = 'gpt-3.5-turbo'
    default_params = {
        # 'max_tokens': 80,
        # 'temperature': 0.8,
        # 'top_p': 1,
        # 'frequency_penalty': 0.5,
        # 'presence_penalty': 0,
    }
    timeout = None
    verbose = False
    show_tokens = False
    history = None
    write_history = False
    debug = False


lg = logging.getLogger(__name__)

home = os.path.expanduser('~')


def main():
    # the `formatter_class` can make description & epilog show multiline
    parser = argparse.ArgumentParser(description="A simple CLI for ChatGPT API", epilog="", formatter_class=argparse.RawDescriptionHelpFormatter)

    # arguments
    parser.add_argument('prompt', metavar="PROMPT", type=str, nargs='?', help="your prompt, leave it empty to run REPL. you can use @ to load prompt from ~/.ai_py_prompts.json")

    # options
    parser.add_argument('-s', '--system', type=str, help="system message to use at the beginning of the conversation. if starts with @, the message will be located through ~/.ai_py_prompts.json")
    parser.add_argument('-c', '--conversation', action='store_true', help="enable conversation, which means all the messages will be sent to the API, not just the last one. This is only useful to REPL")
    parser.add_argument('--history', type=str, help="load the history from a JSON file.")
    parser.add_argument('-w', '--write-history', action='store_true', help="write new messages to --history file after each chat.")
    parser.add_argument('-v', '--verbose', action='store_true', help="verbose mode, show execution info and role in the message")
    parser.add_argument('-t', '--show-tokens', action='store_true', help="show a breakdown of the tokens used in the prompt and in the response")
    parser.add_argument('-d', '--debug', action='store_true', help="debug mode, enable logging")

    # --version
    parser.add_argument('--version', action='version',
        version='%(prog)s {version}'.format(version=__version__))

    args = parser.parse_args()

    # config
    # load config from file
    config_file = os.path.join(home, '.ai_py_config.json')
    if os.path.exists(config_file):
        with open(config_file) as f:
            config = json.load(f)
        for k, v in config.items():
            setattr(Config, k, v)
    # override config from env
    env_api_key = os.environ.get('AI_PY_API_KEY')
    if env_api_key:
        Config.api_key = env_api_key
    env_api_base_url = os.environ.get('AI_PY_API_BASE_URL')
    if env_api_base_url:
        Config.api_base_url = env_api_base_url
    # override config from args
    Config.verbose = args.verbose
    Config.debug = args.debug
    Config.show_tokens = args.show_tokens
    if args.write_history and not args.history:
        print(red('ERROR: --write-history requires --history'))
        exit(1)
    Config.history = args.history
    Config.write_history = args.write_history
    if Config.debug:
        logging.basicConfig(level=logging.DEBUG)
    # check config
    if not Config.api_key:
        print(red('ERROR: missing API key'))
        print(f'Please set the environment variable AI_PY_API_KEY or set api_key in {config_file}')
        exit(1)
    if not Config.api_base_url:
        print(red('ERROR: missing API base url'))
        print(f'Please set the environment variable AI_PY_API_BASE_URL or set api_base_url in {config_file}')
        exit(1)

    # only read stdin when it's not a tty (which means in a pipe) to ensure it won't affect input()
    if not sys.stdin.isatty():
        stdin = sys.stdin.read().strip()
        if stdin:
            args.prompt = f'{args.prompt} {stdin}'

    # load prompts
    pm = PromptsManager()
    pm.load_from_file()

    # create session
    session = ChatSession(Config.api_base_url, Config.api_key, conversation=args.conversation)
    # load history
    if args.history:
        if os.path.exists(args.history):
            session.messages = load_history(args.history)
        else:
            # when write_history is False, history must exist
            if not args.write_history:
                print(red(f'ERROR: history file not found: {args.history}'))
                exit(1)
    # set system message
    if args.system:
        session.set_system_message(pm.new_message('system', args.system))
    if Config.verbose:
        print_info(session)
    for i in session.messages:
        if i['role'] == 'system' or Config.verbose:
            print_message(i)

    # call the function
    if args.prompt:
        chat_once(session, pm, args.prompt)
    else:
        repl(session, pm)


def chat_once(session, pm, prompt):
    user_message = pm.new_message('user', prompt)
    try:
        res_message = session.chat(user_message)
    except TimeoutError:
        print(red('ERROR: timeout'))
        return
    except KeyboardInterrupt:
        print('chat interrupted')
        return
    if Config.verbose or Config.show_tokens:
        print_message(user_message)
    print_message(res_message)

    if Config.write_history:
        write_history(Config.history, session.messages)


def repl(session, pm):
    green_start = esc(32)
    while True:
        try:
            prompt = input(f'{green_start}> ')
        except (KeyboardInterrupt, EOFError):
            print(END, end='')
            print('exit')
            break
        print(END, end='')
        if not prompt:
            continue
        print()
        if prompt in ['exit', 'quit']:
            break

        # special commands
        if prompt.startswith('!'):
            try:
                run_command(session, pm, prompt)
            except Exception as e:
                print(red(f'command failed: {e}'))
        else:
            chat_once(session, pm, prompt)


def load_history(history_file):
    with open(history_file) as f:
        messages = json.load(f)
    # check messages schema
    valid_keys = {'role', 'content'}
    valid_roles = ['user', 'system', 'assistant']
    for i in messages:
        if set(i.keys()) != valid_keys:
            raise ValueError(f'invalid message from history: {i}')
        assert i['role'] in valid_roles, f'invalid role: {i["role"]}'
        assert isinstance(i['content'], str), f'invalid content (wrong type): {i["content"]}'
    return messages


def write_history(history_file, messages):
    with open(history_file, 'w') as f:
        json.dump(messages, f, indent=2)


command_set_keys = ['model', 'params', 'system', 'conversation', 'verbose']

def run_command(session, pm, prompt):
    sp = prompt.split(' ')
    command = sp[0][1:]
    args = sp[1:]

    success_color = magenta
    if command == 'set':
        assert len(args) > 1, f'set command requires at least 2 arguments, got {args}'
        set_key = args[0]
        assert set_key in command_set_keys, f'set key is not one of {command_set_keys}'

        result = ''
        # !set verbose True
        if set_key == 'verbose':
            Config.verbose = str_to_value(args[1], bool)
            result = f'Config.verbose = {Config.verbose}'
        # !set conversation True
        elif set_key == 'conversation':
            session.conversation = str_to_value(args[1], bool)
            result = f'session.conversation = {session.conversation}'
        # !set system you are a poet
        elif set_key == 'system':
            session.set_system_message(pm.new_message('system', ' '.join(args[1:])))
            result = f'session.messages[0] = {session.messages[0]}'
        # !set params temperature 0.5
        elif set_key == 'params':
            session.params[args[1]] = str_to_value(args[2])
            result = f'session.params = {session.params}'
        # !set model gpt-4
        elif set_key == 'model':
            session.model = args[1]
            result = f'session.model = {session.model}'
        if result:
            print(success_color(result))
    elif command == 'info':
        print_info(session)
    elif command == 'write-history':
        write_history(args[0], session.messages)
        print(success_color(f'wrote history to {args[0]}'))
    else:
        raise Exception(f'unknown command: {command}')


def str_to_value(s, assert_type=None):
    v = s
    if s == 'True':
        v = True
    elif s == 'False':
        v = False
    elif re.search(r'^\d+$', s):
        v = int(s)
    elif re.search(r'^\d+\.\d+$', s):
        v = float(s)
    if assert_type is not None:
        assert isinstance(v, assert_type), f'str_to_value failed: {s} is not of type {assert_type}'
    return v


inline_code_re = re.compile(r'`([^\n`]+)`')
multiline_code_re = re.compile(r'```\w*\n([^`]+)\n```')


def print_message(message):
    role = message['role']
    role_with_padding = f' {role} '
    content = message['content'].strip()

    # find inline code and replace with color for non-user messages
    if role != 'user':
        content = multiline_code_re.sub(lambda m: m.group(0).replace(m.group(1), cyan(m.group(1))), content)
        content = inline_code_re.sub(lambda m: m.group(0).replace(m.group(1), cyan(m.group(1))), content)

    content_color = lambda s: s
    role_color = white_hl
    if role == 'system':
        content_color = yellow
        role_color = yellow_hl
    elif role == 'user':
        content_color = green
        role_color = green_hl

    s = content_color(content)
    if (Config.verbose):
        s = f'{role_color(role_with_padding)} {s}'

    print(s + '\n')


def print_info(session):
    c = magenta
    s = f"""\
{magenta_hl(" execution info ")}:
{c('Config')}
    {c('api_base_url')}: {Config.api_base_url}
    {c('api_key')}: {Config.api_key[:5]}******{Config.api_key[-2:]}
{c('ChatSession')}
    {c('model')}: {session.model}
    {c('params')}: {json.dumps(session.params)}
    {c('conversation')}: {session.conversation}
    {c('messages')}: {len(session.messages)}\
"""
    print(s + '\n')


# Prompts #

shortcut_re = re.compile(r'@(\w+)')

class PromptsManager:
    def __init__(self):
        self.data = {}

    def load_from_file(self):
        prompts_file = os.path.join(home, '.ai_py_prompts.json')
        if os.path.exists(prompts_file):
            with open(prompts_file) as f:
                self.data = json.load(f)
        lg.debug(f'prompts loaded: {self.data}')

    def get(self, role, name, default=None):
        return self.data.get(role, {})[name]

    def format_prompt(self, prompt, role):
        def handle_match(m):
            try:
                return self.get(role, m.group(1))
            except KeyError:
                return m.group(0)
        return shortcut_re.sub(handle_match, prompt)

    def new_message(self, role, prompt):
        return {
            'role': role,
            'content': self.format_prompt(prompt, role),
        }


# Session #

class ChatSession:
    def __init__(self, api_base_url, api_key, conversation=False, messages=None, model=None, params=None):
        self.api_base_url = api_base_url
        self.api_key = api_key
        self.conversation = conversation
        if messages is None:
            messages = []
        self.messages = messages

        if not model:
            model = Config.default_model
        self.model = model

        if not params:
            params = Config.default_params
        self.params = params

    def set_system_message(self, system_message):
        """Set the system message to the first system message in the session, or insert a new system message if none exists."""
        messages = self.filter_system_messages()
        if messages:
            messages[0]['content'] = system_message['content']
        else:
            self.messages.insert(0, system_message)

    def filter_system_messages(self):
        return list(filter(lambda x: x['role'] == 'system', self.messages))

    def chat(self, user_message, params_override=None):
        self.messages.append(user_message)
        res_message, data, messages = self.create_completion(params_override=params_override)
        if Config.verbose:
            print(blue(f'stat: sent_messages={len(messages)} total_messages={len(self.messages)}  price=~${"{:.6f}".format(data["usage"]["total_tokens"]/1000*0.002)}'))
        if Config.show_tokens:
            print(blue(f'tokens: prompt_tokens={data["usage"]["prompt_tokens"]} completion_tokens={data["usage"]["completion_tokens"]} total_tokens={data["usage"]["total_tokens"]}'))
        self.messages.append(res_message)
        return res_message

    def create_completion(self, params_override=None) -> tuple[dict, dict, list]:
        url = f'{self.api_base_url}chat/completions'
        headers = {
            # if User-Agent is not added, cloudflare workers will return 403, no idea why it happens
            'User-Agent': 'reorx/ai',
            'Authorization': f'Bearer {self.api_key}',
        }

        if self.conversation:
            messages = self.messages
        else:
            messages = self.filter_system_messages()
            # assume the last message is always the user message
            messages.append(self.messages[-1])

        data = dict(self.params)
        if params_override:
            data.update(params_override)
        data.update(
            model=self.model,
            messages=messages,
        )

        try:
            res, body_b = http_request('POST', url, headers=headers, data=data, logger=lg, timeout=Config.timeout)
        except HTTPError as e:
            raise RequestError(e.status, e.read().decode()) from None
        res_data = json.loads(body_b)
        res_message = res_data['choices'][0]['message']

        return res_message, res_data, messages


# HTTP request #

def http_request(method, url, params=None, headers=None, data: Optional[Union[dict, list, bytes]] = None, timeout=None, logger=None) -> Tuple[HTTPResponse, bytes]:
    if params:
        url = f'{url}?{parse.urlencode(params)}'
    if not headers:
        headers = {}
    if data and isinstance(data, (dict, list)):
        data = json.dumps(data, ensure_ascii=False).encode()
        if 'Content-Type' not in headers:
            headers['Content-Type'] = 'application/json'
    if logger:
        logger.debug(f'request: {method} {url}\nheaders: {headers}\ndata: {data}')
    req = request.Request(url, method=method, headers=headers, data=data)
    res = request.urlopen(req, timeout=timeout)  # raises: (HTTPException, urllib.error.HTTPError)
    try:
        body_b: bytes = res.read()
    except IncompleteRead as e:
        body_b: bytes = e.partial
    if logger:
        logger.debug(f'response: {res.status}, {body_b}')
    return res, body_b


class RequestError(Exception):
    def __init__(self, status, body) -> None:
        self.status = status
        self.body = body

    def __str__(self):
        return f'{self.__class__.__name__}: {self.status}, {self.body}'


# Color #

def esc(*codes: Union[int, str]) -> str:
    """Produces an ANSI escape code from a list of integers
    :rtype: text_type
    """
    return '\x1b[{}m'.format(';'.join(str(c) for c in codes))


def make_color(start, end: str) -> Callable[[str], str]:
    def color_func(s: str) -> str:
        return start + s + end
    return color_func


END = esc(0)

FG_END = esc(39)
black = make_color(esc(30), FG_END)
red = make_color(esc(31), FG_END)
green = make_color(esc(32), FG_END)
yellow = make_color(esc(33), FG_END)
blue = make_color(esc(34), FG_END)
magenta = make_color(esc(35), FG_END)
cyan = make_color(esc(36), FG_END)
white = make_color(esc(37), FG_END)

BG_END = esc(49)
black_bg = make_color(esc(40), BG_END)
red_bg = make_color(esc(41), BG_END)
green_bg = make_color(esc(42), BG_END)
yellow_bg = make_color(esc(43), BG_END)
blue_bg = make_color(esc(44), BG_END)
magenta_bg = make_color(esc(45), BG_END)
cyan_bg = make_color(esc(46), BG_END)
white_bg = make_color(esc(47), BG_END)

HL_END = esc(22, 27, 39)
#HL_END = esc(22, 27, 0)

black_hl = make_color(esc(1, 30, 7), HL_END)
red_hl = make_color(esc(1, 31, 7), HL_END)
green_hl = make_color(esc(1, 32, 7), HL_END)
yellow_hl = make_color(esc(1, 33, 7), HL_END)
blue_hl = make_color(esc(1, 34, 7), HL_END)
magenta_hl = make_color(esc(1, 35, 7), HL_END)
cyan_hl = make_color(esc(1, 36, 7), HL_END)
white_hl = make_color(esc(1, 37, 7), HL_END)

bold = make_color(esc(1), esc(22))
italic = make_color(esc(3), esc(23))
underline = make_color(esc(4), esc(24))
strike = make_color(esc(9), esc(29))
blink = make_color(esc(5), esc(25))


if __name__ == '__main__':
    main()
