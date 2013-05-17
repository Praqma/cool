package net.praqma.cli;

import java.io.File;
import java.util.logging.Logger;

import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.exceptions.UCMEntityNotFoundException;
import net.praqma.clearcase.exceptions.UnableToCreateEntityException;
import net.praqma.clearcase.exceptions.UnableToGetEntityException;
import net.praqma.clearcase.exceptions.UnableToInitializeEntityException;
import net.praqma.clearcase.exceptions.UnableToLoadEntityException;
import net.praqma.clearcase.ucm.entities.Project;
import net.praqma.clearcase.ucm.entities.Stream;
import net.praqma.clearcase.ucm.utils.BuildNumber;
import net.praqma.util.option.Option;
import net.praqma.util.option.Options;

public class GetNextBuildNumber {
	private static Logger logger = Logger.getLogger( GetNextBuildNumber.class.getName() );

	public static void main( String[] args ) throws ClearCaseException {
		try {
			run( args );
		} catch( ClearCaseException e ) {
			e.print( System.err );
			throw e;
		}
	}

	public static void run( String[] args ) throws UCMEntityNotFoundException, UnableToLoadEntityException, UnableToCreateEntityException, UnableToGetEntityException, UnableToInitializeEntityException {
		Options o = new Options( net.praqma.cool.Version.version );

		Option oproject = new Option( "project", "p", false, 1, "Retrieve the next build number given a project" );
		Option ostream = new Option( "stream", "s", false, 1, "Retrieve the next build number given a stream" );
		o.setOption( oproject );
		o.setOption( ostream );

		o.setDefaultOptions();

		o.setSyntax( "GetNextBuildNumber [-p {project} | -s {stream}]" );
		o.setHeader( "Retrieve the next build number given a UCM project or a UCM stream." );

		o.parse( args );

		try {
			o.checkOptions();
		} catch( Exception e ) {
			logger.severe( "Incorrect option: " + e.getMessage() );
			o.display();
			System.exit( 1 );
		}

		if( !oproject.used && !ostream.used ) {
			logger.severe( "Neither a stream nor a project was given." );
			System.exit( 1 );
		}

		Project project = null;

		if( oproject.used ) {
			project = Project.get( oproject.getString() ).load();
		} else {
			Stream stream = Stream.get( ostream.getString() ).load();
			project = stream.getProject();
		}

		try {
			String number = BuildNumber.getBuildNumber( project );
			logger.info( number );
			System.exit( 0 );
		} catch( ClearCaseException e ) {
			logger.severe( "Could not retrieve the build number" );
			System.exit( 1 );
		}
	}
}
