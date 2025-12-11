package hu.porkolab.chaosSymphony.common.idemp;

public interface IdempotencyStore {
	
	boolean markIfFirst(String eventId);
}
