package net.praqma.clearcase.ucm.view;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.clearcase.ClearCase;
import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.exceptions.CleartoolException;
import net.praqma.clearcase.exceptions.UnableToInitializeEntityException;
import net.praqma.clearcase.exceptions.ViewException;
import net.praqma.clearcase.exceptions.ViewException.Type;
import net.praqma.clearcase.ucm.entities.Activity;
import net.praqma.clearcase.ucm.entities.Project;
import net.praqma.clearcase.ucm.entities.Stream;
import net.praqma.util.execute.CmdResult;

public class UCMView extends ClearCase implements Serializable {

	public static final Pattern rx_view_get_path = Pattern.compile( "^\\s*Global path:\\s*(.*?)\\s*$" );

	transient private static Logger logger = Logger.getLogger( UCMView.class.getName() );
	
	private static Map<String, UCMView> createdViews = new HashMap<String, UCMView>();

	protected String path;
	protected String viewtag = "";
	protected Stream stream = null;
	
	protected String storageLocation = null;
	
	protected boolean dynamic = false;

	public UCMView() {
	}

	public UCMView(String path) {
		this.path = path;
	}
	
	public UCMView(String path, String viewtag) {
		this.path = path;
		this.viewtag = viewtag;
	}
	
	public UCMView(String path, String viewtag, Stream stream) {
		this.path = path;
		this.viewtag = viewtag;
		this.stream = stream;
	}

	public static boolean viewExists(String viewtag) {
		logger.fine( viewtag );

		String cmd = "lsview " + viewtag;

		try {
			String s = Cleartool.run( cmd ).stdoutBuffer.toString();
			logger.fine( viewtag + " exists" );
			return true;
		} catch( Exception e ) {
			logger.fine( "View check failed: " + e.getMessage() );
			return false;
		}
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}
	
	public String getViewtag() {
		return this.viewtag;
	}
	
	public void remove() throws ViewException {
		//context.removeView( this );
		String cmd = "rmview -force " + ( isDynamicView() ? "-tag " + viewtag : ( storageLocation != null ? storageLocation : "-tag " + viewtag ) );

		try {
			Cleartool.run( cmd );
		} catch( Exception e ) {
			throw new ViewException( "Unable to remove " + this, path, Type.REMOVE_FAILED, e );
		}
	}
	
	public boolean isDynamicView() {
		return this.dynamic;
	}
	
	public UCMView load() throws ViewException {
		//Map<String, String> options = context.loadView( this );
		
		logger.fine( "Loading view " + this );

		String cmd = "lsview -l " + getViewtag();

		Map<String, String> options = new HashMap<String, String>();

		try {
			CmdResult r = Cleartool.run( cmd );

			for( String s : r.stdoutList ) {
				if( s.contains( "Global path" ) ) {
					Matcher m = rx_view_get_path.matcher( s );
					if( m.find() ) {
						options.put( "pathname", m.group( 1 ) );
					}
				}
			}

		} catch( Exception e ) {
			throw new ViewException( "Unable to load " + this, this.path, Type.LOAD_FAILED, e );
		}

		try {
			this.storageLocation = options.get("pathname");
		} catch( NullPointerException e ) {
			throw new ViewException( "No storage location", this.path, Type.LOAD_FAILED, e );
		}
		
		return this;
	}
	
	public String getStorageLocation() {
		return this.storageLocation;
	}
	
	public Stream getStream() throws UnableToInitializeEntityException, CleartoolException, ViewException, IOException {
		return stream;
	}
	
	public UCMView end() throws ViewException {
		end( viewtag );
		return this;
	}
	
	public static void end( String viewtag ) throws ViewException {
		try {
			Cleartool.run( "endview -server " + viewtag );
		} catch( Exception e ) {
			throw new ViewException( "Could not end view " + viewtag, null, Type.END_VIEW_FAILED, e );
		}
	}
	
	public UCMView start() throws ViewException {
		start( viewtag );
		return this;
	}
	
	public static void start( String viewtag ) throws ViewException {
		try {
			Cleartool.run( "startview " + viewtag );
		} catch( Exception e ) {
			throw new ViewException( "Could not start view " + viewtag, null, Type.END_VIEW_FAILED, e );
		}
	}
	
	public static void getViews( Project project ) {
		// cleartool lsstream -in project:ava2@\chw_PVOB
		// http://publib.boulder.ibm.com/infocenter/cchelp/v7r0m0/index.jsp?topic=/com.ibm.rational.clearcase.cc_ref.doc/topics/ct_lsstream.htm
	}
	
	public static UCMView getView( String viewTag ) throws ViewException {
		if( viewTag.trim().equals( "" ) ) {
			throw new ViewException( "View tag cannot be empty", "", Type.EMPTY );
		}
		
		UCMView v = new UCMView();
		v.viewtag = viewTag;
		
		return v;
	}
	
	
	public String toString() {
		return viewtag;
	}
	
	protected static void create( String tag, String stgloc, boolean snapshotView, Stream stream ) throws ViewException {
		logger.fine( "Creating " + tag );

		String cmd = "mkview -tag " + tag + ( snapshotView ? " -snapshot" : "" ) + ( stream != null ? " -stream " + stream.getFullyQualifiedName() : "" ) + " -stgloc " + ( stgloc != null ? stgloc : "-auto" );

		try {
			Cleartool.run( cmd );
		} catch( Exception e ) {
			throw new ViewException( "Unable to create view " + tag, stgloc, Type.CREATION_FAILED, e );
		}
	}
	
	public static void setActivity( Activity activity, File context, String viewtag, String comment ) throws ViewException {
		String cmd = "setactivity " + ( comment != null ? "-c \"" + comment + "\" " : "-nc " ) +
									  ( viewtag != null ? "-viewtag " + viewtag + " " : "" ) + 
									  ( activity != null ? activity.getNormalizedName() : "-none" );

		try {
			Cleartool.run( cmd, context );
		} catch( Exception e ) {
			throw new ViewException( "Unable to set activity " + activity, context.toString(), Type.ACTIVITY_FAILED, e );
		}
	}
	
	@Override
	public boolean equals( Object other ) {
		if( other instanceof UCMView ) {
			return ((UCMView)other).viewtag.equals( viewtag );
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + ( null == viewtag ? 0 : viewtag.hashCode() );
		return hash;
	}

	@Override
	public String getFullyQualifiedName() {
		return viewtag;
	}

}