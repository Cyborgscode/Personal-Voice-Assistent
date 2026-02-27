package server;

import data.Command;
import server.PVA;
import java.util.concurrent.LinkedBlockingQueue;


public class IntentWorker extends Thread {
	private final LinkedBlockingQueue<IntentPacket> queue = new LinkedBlockingQueue<>();

	PVA pva;

	public IntentWorker(PVA pva) {
		this.pva = pva;
		this.setName("PVA-IntentWorker");
		this.setDaemon(true); // Stirbt brav mit dem Hauptprozess
	}

	public void enqueue(Command cmd, String data) {
		queue.offer(new IntentPacket(cmd, data));
	}

	public void run() {
		while (!isInterrupted()) {
			try {
				// Blockiert, bis ein Intent reinkommt
				IntentPacket packet = queue.take();
				
				// Kausalität gewahrt: Der PLS verarbeitet es nacheinander
				// ASYNC -> SYNC
				pva.sendIntent(packet.cmd, packet.data);
				
			} catch (InterruptedException e) {
				break;
			}
		}
	}
}

