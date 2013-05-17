package net.praqma.clearcase.ucm.entities;

import java.io.File;
import java.util.logging.Logger;

import net.praqma.clearcase.PVob;
import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.exceptions.*;
import net.praqma.util.execute.AbnormalProcessTerminationException;

public class Component extends UCMEntity {
	
	private transient static Logger logger = Logger.getLogger( Component.class.getName()  );
	
	private static final String rx_component_load = "\\s*Error: component not found\\s*";

	Component() {
		super( "component" );
	}

	static Component getEntity() {
		return new Component();
	}

	public Component load() throws UCMEntityNotFoundException, UnableToLoadEntityException {
		String cmd = "describe -fmt %[name]p " + this;
		try {
			Cleartool.run( cmd );
		} catch( AbnormalProcessTerminationException e ) {
			if( e.getMessage().matches( rx_component_load ) ) {
				throw new UCMEntityNotFoundException( this, e );
			} else {
				throw new UnableToLoadEntityException( this, e );
			}
		}
		
		this.loaded = true;

		return this;
	}

	public static Component create( String name, PVob pvob, String root, String comment, File view ) throws UnableToCreateEntityException, UnableToInitializeEntityException {

		String cmd = "mkcomp" + ( comment != null ? " -c \"" + comment + "\"" : "" ) + ( root != null ? " -root " + root : " -nroot" ) + " " + name + "@" + pvob;

		try {
			Cleartool.run( cmd, view );
		} catch( Exception e ) {
			throw new UnableToCreateEntityException( Component.class, e );
		}

		return get( name, pvob );
	}

	public String getRootDir() throws CleartoolException {
		String cmd = "desc -fmt %[root_dir]p " + this;
		try {
			return Cleartool.run( cmd ).stdoutBuffer.toString();
		} catch( AbnormalProcessTerminationException e ) {
			throw new CleartoolException( "Unable to get root dir: " + e.getMessage(), e );
		}
	}

    public boolean isRootLess() throws CleartoolException {
        return getRootDir().isEmpty();
    }
	
	public static Component get( String name, PVob pvob ) throws UnableToInitializeEntityException {
		if( !name.startsWith( "component:" ) ) {
			name = "component:" + name;
		}
		Component entity = (Component) UCMEntity.getEntity( Component.class, name + "@" + pvob );
		return entity;
	}
	
	public static Component get( String name ) throws UnableToInitializeEntityException {
		if( !name.startsWith( "component:" ) ) {
			name = "component:" + name;
		}
		Component entity = (Component) UCMEntity.getEntity( Component.class, name );
		return entity;
	}
        
        /**
         * Unique hash code for object based on fully qualified nave
         * Neccessary if adding objects to hash sets, -maps etc.
         * @return unique hash value for object.
         */
        @Override
        public int hashCode() {
            // check for load not needed fqname always given
            // FullyQualifiedName is unique in ClearCase (at least in this installation)
            return (this.getFullyQualifiedName().hashCode());
        }

}
