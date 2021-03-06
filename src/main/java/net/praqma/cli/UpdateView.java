package net.praqma.cli;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.*;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.ucm.view.SnapshotView;
import net.praqma.clearcase.ucm.view.SnapshotView.Components;
import net.praqma.clearcase.ucm.view.SnapshotView.LoadRules2;
import net.praqma.clearcase.ucm.view.SnapshotView.UpdateInfo;
import net.praqma.util.option.Option;
import net.praqma.util.option.Options;

@SuppressFBWarnings("")
public class UpdateView {
	private static final Logger logger = Logger.getLogger( UpdateView.class.getName() );
	
	public static void main( String[] args ) throws ClearCaseException, IOException {
		try {
			run( args );
		} catch( ClearCaseException e ) {
			e.print( System.err );
			throw e;
		}
	}

	public static void run( String[] args ) throws ClearCaseException, IOException {
		Options o = new Options();

		Option opath = new Option( "path", "p", false, 1, "ClearCase view to be cleaned" );
		Option oswipe = new Option( "swipe", "s", false, 0, "Swipe view" );
		Option ogen = new Option( "generate", "g", false, 0, "Generate stream" );
		Option ooverwrite = new Option( "overwrite", "o", false, 0, "Overwrite" );
		
		Option oall = new Option( "all", "a", false, 0, "All components" );
		Option omodifiable = new Option( "modifiable", "m", false, 0, "Modifiable components" );

		o.setOption( opath );
		o.setOption( oswipe );
		o.setOption( ogen );
		o.setOption( ooverwrite );
		
		o.setOption( oall );
		o.setOption( omodifiable );

        o.setDefaultOptions();
        
        o.parse( args );
        
		try {
			o.checkOptions();
		} catch( Exception e ) {
			logger.severe( "Incorrect option: " + e.getMessage() );
			o.display();
			System.exit( 1 );
		}
		
		File viewroot = null;
		if( opath.isUsed() ) {
			viewroot = new File( opath.getString() );
		} else {
			viewroot = new File( System.getProperty( "user.dir" ) );
		}
		
		boolean swipe = false;
		if( oswipe.isUsed() ) {
			swipe = true;
		}
		
		boolean generate = false;
		if( ogen.isUsed() ) {
			generate = true;
		}
		
		boolean overwrite = false;
		if( ooverwrite.isUsed() ) {
			overwrite = true;
		}
		
		if( oall.isUsed() && omodifiable.isUsed() ) {
			logger.severe( "Only all or modifiable can be chosen" );
			o.display();
			System.exit( 1 );
		}
		
		if( !oall.isUsed() && !omodifiable.isUsed() ) {
			logger.severe( "One of all and modifiable must be chosen" );
			o.display();
			System.exit( 1 );
		}

		SnapshotView view = SnapshotView.getSnapshotViewFromPath( viewroot );
		
		LoadRules2 loadRules = null;
		if( oall.isUsed() ) {
			loadRules = new LoadRules2(Components.ALL );
		} else {
			loadRules = new LoadRules2(Components.MODIFIABLE );
		}

		UpdateInfo info = view.update( swipe, generate, overwrite, false, loadRules );
		
		if( swipe ) {
			logger.info( "Removed " + info.filesDeleted + " file" + ( info.filesDeleted == 1 ? "" : "s" ) );
			logger.info( "Removed " + info.dirsDeleted + " director" + ( info.dirsDeleted == 1 ? "y" : "ies" ) );		
		}
	}

}
