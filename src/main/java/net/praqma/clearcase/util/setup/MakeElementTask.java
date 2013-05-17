package net.praqma.clearcase.util.setup;

import java.io.File;
import java.io.IOException;

import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.ucm.entities.Version;
import net.praqma.clearcase.util.setup.EnvironmentParser.Context;

import org.w3c.dom.Element;

public class MakeElementTask extends AbstractTask {

	@Override
	public void parse( Element e, Context context ) throws ClearCaseException {
		File file = new File( e.getAttribute( "file" ) );
		String comment = getComment( e, context );
		
		File newfile = new File( context.path, e.getAttribute( "file" ) );
		
		if( !newfile.exists() ) {
			try {
				newfile.createNewFile();
			} catch( IOException e1 ) {
				throw new ClearCaseException( e1 );
			}
		}
		
		Version.makeElement( file, context.path, comment );
	}

}
