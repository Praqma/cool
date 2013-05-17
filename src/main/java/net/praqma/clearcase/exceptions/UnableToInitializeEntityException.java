package net.praqma.clearcase.exceptions;

import net.praqma.clearcase.ucm.entities.UCMEntity;

//public class UCMException extends RuntimeException
public class UnableToInitializeEntityException extends ClearCaseException {
	
	Class<? extends UCMEntity> clazz;

	public UnableToInitializeEntityException( Class<? extends UCMEntity> clazz, Exception e ) {
		super( e );
		this.clazz = clazz;
	}
	
	public Class<? extends UCMEntity> getClazz() {
		return clazz;
	}
	
}