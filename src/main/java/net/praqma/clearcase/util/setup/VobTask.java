package net.praqma.clearcase.util.setup;

import net.praqma.clearcase.PVob;
import net.praqma.clearcase.Vob;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.exceptions.EntityAlreadyExistsException;
import net.praqma.clearcase.util.SetupUtils;
import net.praqma.clearcase.util.setup.EnvironmentParser.Context;

import org.w3c.dom.Element;

import java.util.logging.Logger;

public class VobTask extends AbstractTask {
	
	private static Logger logger = Logger.getLogger( VobTask.class.getName() );

	@Override
	public void parse( Element e, Context context ) throws ClearCaseException {
		boolean ucm = e.getAttribute( "ucmproject" ).length() > 0;
		String store = getValue( "storetag", e, context, null );
		String tag = getTag( e, context );
		String location = e.getAttribute( "stgloc" );
		boolean mount = e.getAttribute( "mounted" ).length() > 0;
		
		try {
			if( ucm ) {
				/* TODO Add a test attribute to the pvob */
				PVob vob = PVob.create( tag, location, null );
				context.pvobs.put( tag, vob );
				if( mount ) {
					vob.mount();
				}
				vob.load();
			} else {
				Vob vob = Vob.create( tag, ucm, location, null );
				if( mount ) {
					vob.mount();
				}
				vob.load();
			}
		} catch( EntityAlreadyExistsException e1 ) {
			if( ucm ) {
				logger.fine( "The pvob already exists, tear it down" );
				/* TODO Make sure this pvob has a test attribute */
				
				PVob vob = new PVob( tag );
				/* Tear it down */
				SetupUtils.tearDown( vob );
				
				/* TODO Add a test attribute to the pvob */
				vob = PVob.create( tag, location, null );
				context.pvobs.put( tag, vob );
				if( mount ) {
					vob.mount();
				}
				vob.load();
			} else {
				Vob vob = new Vob( tag );
				if( mount ) {
					vob.mount();
				}
				vob.load();
			}
			
		}
		
		/* Store */
		if( store != null ) {
			context.put( store, tag );
		}
		
	}

}
