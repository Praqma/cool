package net.praqma.clearcase.ucm.entities;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.praqma.clearcase.Cool;
import net.praqma.clearcase.Describe;
import net.praqma.clearcase.PVob;
import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.exceptions.*;
import net.praqma.clearcase.interfaces.Diffable;
import net.praqma.clearcase.ucm.entities.Project.PromotionLevel;

import net.praqma.util.execute.AbnormalProcessTerminationException;

public class Baseline extends UCMEntity implements Diffable {

	transient static private Logger logger = Logger.getLogger( Baseline.class.getName()  );

    /**
     * The {@link Component} of the {@link Baseline}
     */
	private Component component = null;

    /**
     * The {@link PromotionLevel} of the {@link Baseline}
     */
	private Project.PromotionLevel plevel = Project.PromotionLevel.INITIAL;

    /**
     * The {@link Stream} of the {@link Baseline}
     */
	private Stream stream = null;

    /**
     * The {@link Activity}ies of the {@link Baseline}
     */
	private ArrayList<Activity> activities = new ArrayList<Activity>();
	
	public enum LabelBehaviour {
		NOLABEL,
		INCREMENTAL,
		FULL,
		full,
		none,
		DEFAULT;
		
		public String toArgument() {
			switch( this ) {
			case NOLABEL:
				return "-nlabel";
			case INCREMENTAL:
				return "-incremental";
			case FULL:
				return "-full";
			case full:
				return "-full";
			case none:
				return "-nlabel";
			default:
				return "";
			}
		}
		
		public static LabelBehaviour fromIncremental( boolean incremental ) {
			return ( incremental ? INCREMENTAL : FULL );
		}
	}

	Baseline() {
		super( "baseline" );
	}

	/**
	 * Load the Baseline into memory from ClearCase.<br>
	 * This function is automatically called when needed by other functions.
	 * 
	 * @throws UnableToLoadEntityException
	 * @throws UnableToInitializeEntityException 
	 * @throws UCMEntityNotFoundException 
	 * @throws UnableToCreateEntityException 
	 * @throws UnableToGetEntityException 
	 */
	public Baseline load() throws UnableToLoadEntityException, UnableToInitializeEntityException {

		String result = "";

		String cmd = "desc -fmt %n" + Cool.delim + "%X[component]p" + Cool.delim + "%X[bl_stream]p" + Cool.delim + "%[plevel]p" + Cool.delim + "%u" + Cool.delim + "%Nd" + Cool.delim + "%[label_status]p" + Cool.delim + "%[master]p " + this;
		try {
			result = Cleartool.run( cmd ).stdoutBuffer.toString();
		} catch( Exception e ) {
			throw new UnableToLoadEntityException( this, e );
		}

		String[] rs = result.split( UCMEntity.delim );
        logger.fine( "Result:" + result );

		/* Component */
		String c = ( rs[1].matches( "^component:.*$" ) ? "" : "component:" ) + ( rs[1].matches( ".*@" + PVob.rx_tag_format + "$" ) ? rs[1] : rs[1] + "@" + this.pvob );
		/* Stream */
		if( rs[2].trim().length() > 0 ) {
			String s = ( rs[2].matches( "^stream:.*$" ) ? "" : "stream:" ) + ( rs[2].matches( ".*@" + PVob.rx_tag_format + "$" ) ? rs[2] : rs[2] + "@" + this.pvob );
			this.stream = Stream.get( s );
		} else {
			logger.fine( "No stream set for baseline" );
		}

		/* Now with factory creation! */
		this.component = Component.get( c );
		this.plevel = Project.getPlevelFromString( rs[3] );
		this.user = rs[4];
		try {
            logger.fine( "Result[5]:" + rs[5] );
            synchronized( dateFormatter ) {
            	this.date = dateFormatter.parse( rs[5] );
            }
		} catch( ParseException e ) {
			logger.fine( "Unable to parse date: " + e.getMessage() );
			this.date = null;
		}

		this.labelStatus = getLabelStatusFromString( rs[6] );
		
		/* mastership */
		this.mastership = rs[7];

		this.loaded = true;

		return this;
	}
	
	public static Baseline create( String basename, Component component, File view, LabelBehaviour labelBehaviour, boolean identical ) throws UnableToInitializeEntityException, UnableToCreateEntityException, NothingNewException {
		return create( basename, component, view, labelBehaviour, identical, null, null );
	}

	/**
	 * Given a baseline basename, a component and a view, the baseline is
	 * created.
	 */
	public static Baseline create( String basename, Component component, File view, LabelBehaviour labelBehaviour, boolean identical, List<Activity> activities, List<Component> depends ) throws UnableToInitializeEntityException, UnableToCreateEntityException, NothingNewException {
		/* Remove prefixed baseline: */
		if( basename.toLowerCase().startsWith( "baseline:" ) ) {
			basename = basename.replaceFirst( "baseline:", "" );
		}

		boolean created = false;

		String cmd = "mkbl -nc -component " + component.getNormalizedName() + ( identical ? " -identical" : "" );

		if( depends != null && depends.size() > 0 ) {
			cmd += " -adepends_on ";
			for( Component c : depends ) {
				cmd += c.getNormalizedName() + ",";
			}
			cmd = cmd.substring( 0, ( cmd.length() - 1 ) );
		}

		if( activities != null ) {
			cmd += " -activities";
			for( Activity a : activities ) {
				cmd += " " + a.getFullyQualifiedName() + ",";
			}
			cmd = cmd.substring( 0, ( cmd.length() - 1 ) );
		}

		cmd += " " + labelBehaviour.toArgument();
		cmd += " " + basename;

		try {
			String out = "";
			if( view != null ) {
				out = Cleartool.run( cmd, view ).stdoutBuffer.toString();
			} else {
				out = Cleartool.run( cmd ).stdoutBuffer.toString();
			}
			logger.fine( "Baseline output: " + out );

			created = out.matches( "(?s).*Created baseline \".*?\" in component \".*?\".*" ); // Created baseline
		} catch( AbnormalProcessTerminationException e ) {
			throw new UnableToCreateEntityException( Baseline.class, e );
		}

		if( created ) {
			return get( basename, component.getPVob() );
		} else {
			throw new NothingNewException( "No baseline created, nothing new." );
		}
	}

    public PromotionLevel getPromotionLevel() {
        autoLoad();

        return plevel;
    }

	/**
	 * Return the promotion level of a baseline. <br>
	 * If <code>cached</code> is not set, the promotion level is loaded from
	 * ClearCase.
	 * 
	 * @param cached
	 *            Whether to use the cached promotion level or not
	 * @return The promotion level of the Baseline
	 */
	public Project.PromotionLevel getPromotionLevel( boolean cached ) {
		if( cached ) {
			return getPromotionLevel();
		} else {
            //String cmd = "desc -fmt %[plevel]p " + this;
			return getPromotionLevel();
		}
	}

	/**
	 * Promote the Baseline.
	 * <ul>
	 * <li><code>INITIAL -> BUILT</code></li>
	 * <li><code>BUILD&nbsp;&nbsp; -> TESTED</code></li>
	 * <li><code>TESTED&nbsp; -> RELEASED</code></li>
	 * </ul>
	 * 
	 * If the promotion level is not set, it is set to <code>INITAL</code>.
	 * 
	 * @return The new promotion level.
	 * @throws UnableToLoadEntityException
	 * @throws UnableToPromoteBaselineException
	 * @throws UCMEntityNotFoundException 
	 * @throws UnableToCreateEntityException 
	 * @throws UnableToGetEntityException 
	 */
	public Project.PromotionLevel promote() throws UnableToPromoteBaselineException {
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}

		if( this.plevel.equals( PromotionLevel.REJECTED ) ) {
			throw new UnableToPromoteBaselineException( this, PromotionLevel.REJECTED );
		}

		this.plevel = Project.promoteFrom( this.plevel );
		setPromotionLevel( this.plevel );

		return this.plevel;
	}

	/**
	 * Demotes the Baseline to <code>REJECTED</code>.
	 * 
	 * @throws UnableToLoadEntityException
	 * @throws UnableToPromoteBaselineException
	 * @throws UCMEntityNotFoundException 
	 * @throws UnableToCreateEntityException 
	 * @throws UnableToGetEntityException 
	 */
	public Project.PromotionLevel demote() throws UnableToPromoteBaselineException {
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}

		this.plevel = Project.PromotionLevel.REJECTED;
		setPromotionLevel( this.plevel );

		return Project.PromotionLevel.REJECTED;
	}

    /**
     * Set the {@link PromotionLevel}
     * @param plevel
     * @throws UnableToPromoteBaselineException
     */
	public void setPromotionLevel( Project.PromotionLevel plevel ) throws UnableToPromoteBaselineException {
		this.plevel = plevel;

		String cmd = "chbl -level " + plevel + " " + this;
		try {
			Cleartool.run( cmd );
		} catch( AbnormalProcessTerminationException e ) {
			throw new UnableToPromoteBaselineException( this, this.plevel );
		}
	}

	public Component getComponent() {
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}
		
		return this.component;
	}

    /**
     * Get the {@link List} of {@link Baseline}'s, that depends on this {@link Baseline}.
     */
    public List<Baseline> getDependant() throws CleartoolException, UnableToInitializeEntityException {
        logger.fine( "Finding dependant baselines for " + this.getNormalizedName() );

        String[] ds = new Describe( this ).dependentsOn().describe().get( "depends_on" );

        List<Baseline> baselines = new ArrayList<Baseline>( ds.length );
        for( String bl : ds ) {
            baselines.add( Baseline.get( bl ) );
        }

        return baselines;
    }

	public Stream getStream() {
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}
		
		return this.stream;
	}
	
	public boolean shouldResetMastership() throws ClearCaseException {
		if( !getMastership().equals( this.getStream().getOriginalMastership() ) ) {
			return true;
		}
		return false;
	}

	public void resetMastership() throws ClearCaseException {
		this.setMastership( this.getStream().getOriginalMastership() );
	} 

	public String stringify() {
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}
		
		StringBuffer sb = new StringBuffer();

		try {
			if( !this.loaded ) load();

			sb.append( " * Level    : " + this.plevel + linesep );
			sb.append( " * Component: " + this.component.toString() + linesep );
			sb.append( " * Stream   : " + this.stream.toString() + linesep );
			sb.append( " * Date     : " + this.date.toString() + linesep );

		} catch( Exception e ) {

		} finally {
			sb.insert( 0, super.stringify() );
		}

		return sb.toString();
	}

	public static Baseline get( String name ) throws UnableToInitializeEntityException {
		if( !name.startsWith( "baseline:" ) ) {
			name = "baseline:" + name;
		}
		Baseline entity = (Baseline) UCMEntity.getEntity( Baseline.class, name );
		return entity;
	}

	public static Baseline get( String name, PVob pvob ) throws UnableToInitializeEntityException {
		if( !name.startsWith( "baseline:" ) ) {
			name = "baseline:" + name;
		}
		Baseline entity = (Baseline) UCMEntity.getEntity( Baseline.class, name + "@" + pvob );
		return entity;
	}
	
	public List<Baseline> getPostedBaselinesFor(Component component) throws UnableToInitializeEntityException, UnableToListBaselinesException, UnableToLoadEntityException {
		logger.fine( "Getting posted baselines for " + this.getFullyQualifiedName() + " and " + component.getFullyQualifiedName() );
		List<String> bls_str = null;
		
		String cmd = "des -fmt %X[member_of_closure]p " + this.getFullyQualifiedName();
		try {
			bls_str = Cleartool.run( cmd, null, false ).stdoutList;
		} catch( AbnormalProcessTerminationException e ) {
			throw new UnableToListBaselinesException(getStream(), component, getPromotionLevel(true), e );
		}

		List<Baseline> bls = new ArrayList<Baseline>();

		for( String bl_lines : bls_str ) {
			logger.fine( "Baselines " + bl_lines );
			String[] baselines = bl_lines.split(" ");
			logger.fine( "I got " + baselines.length + " baselines." );
			for(String bl : baselines ) {
				logger.fine( "Baseline " + bl );
				Baseline b = Baseline.get( bl ).load();
				logger.fine( "Baseline " + b.getFullyQualifiedName() + " component " + b.getComponent().getFullyQualifiedName() );
				logger.fine( "Component " + component.getFullyQualifiedName());
				logger.fine( "Baseline component " + b.getComponent().getFullyQualifiedName());
				if( b.getComponent().equals(component) ) 
					bls.add( b );
			}
		}

		if( bls.isEmpty()) {
			if( this.getComponent().equals(component) ) { 
				logger.fine( "Baseline " + this.getFullyQualifiedName() + " used because it was not rootless, and matched component " + component.getFullyQualifiedName());
				bls.add(this);
			} else {
				logger.warning( "Could not find a baseline matching component: " + component.getFullyQualifiedName());
			}
		}
		return bls;
	}

}
