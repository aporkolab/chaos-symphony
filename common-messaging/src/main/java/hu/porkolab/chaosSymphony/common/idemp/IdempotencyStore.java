package hu.porkolab.chaosSymphony.common.idemp;

public interface IdempotencyStore {
	/** @return true, ha most láttuk először (tehát feldolgozható) */
	boolean markIfFirst(String eventId);
}
