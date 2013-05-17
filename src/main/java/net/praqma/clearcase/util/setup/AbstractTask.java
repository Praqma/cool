package net.praqma.clearcase.util.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.clearcase.Cool;
import net.praqma.clearcase.PVob;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.util.setup.EnvironmentParser.Context;
import net.praqma.util.execute.CommandLine;
import net.praqma.util.execute.CommandLineInterface.OperatingSystem;

import org.w3c.dom.DOMError;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class AbstractTask {

	public abstract void parse( Element e, Context context ) throws ClearCaseException;
	
	public PVob getPVob( String name ) {
		if( name.length() > 0 ) {
			return new PVob( name );
		} else {
			return null;
		}
	}
	
	private static final Pattern rx_variable = Pattern.compile( "(\\$\\{(.*?)\\})" );
	
	/**
	 * Get the tag of an element. Either tag, wintag or linuxtag must be provided or else null is returned.<br/>
	 * wintag and tag are aliases. wintag is preferred. tag is typically used without prepended backward slash.
	 * @param e - Element tag
	 * @param context - The context
	 * @return The tag given the OS context or null
	 */
	public String getTag( Element e, Context context ) {
		String tag = getValue( "tag", e, context, null ); /* Same as wintag */
		String wintag = getValue( "wintag", e, context, null );
		String lintag = getValue( "linuxtag", e, context, null );
		
		if( Cool.getOS().equals( OperatingSystem.WINDOWS ) ) {
			if( wintag != null ) {
				return wintag;
			} else if( tag != null ) {
				return ( tag.startsWith( "\\" ) ? tag : "\\" + tag );
			} else {
				return null;
			}
		} else {
			return lintag;
		}
	}
	
	public String getValue( String name, Element e, Context context ) {
		return getValue( name, e, context, "" );
	}
	
	public String getValue( String name, Element e, Context context, String def ) {
		String value = e.getAttribute( name );
		
		Matcher m = rx_variable.matcher( value );

		while( m.find() ) {
			String var = context.variables.get( m.group( 2 ) ).value;			
			value = value.replace( m.group( 1 ), var );
		}
		
		return value.length() > 0 ? value : def;
	}
	
	public boolean getBoolean( String name, Element e, Context context, boolean def ) {
		String value = e.getAttribute( name );
		
		value = parseField( value, context );
		
		boolean result = def;
		
		if( value.length() == 0 ) {
			result = false;
		} else {
			if( value.matches( "^(?i)off|false$" ) ) {
				result = false;
			} else {
				result = true;
			}
		}
		
		return result;
	}
	
	public String parseField( String value, Context context ) {
		Matcher m = rx_variable.matcher( value );

		while( m.find() ) {
			String var = context.variables.get( m.group( 2 ) ).value;			
			value = value.replace( m.group( 1 ), var );
		}
		
		return value;
	}
	
	public String getComment( Element e, Context context ) {
		String comment = getValue( "comment", e, context );
		return comment.length() > 0 ? comment : null;
	}
	
    public Element getFirstElement( Element e, String tag ) throws DOMException {
        NodeList list = e.getChildNodes();

        for( int i = 0; i < list.getLength(); i++ ) {
            Node node = list.item( i );

            if( node.getNodeType() == Node.ELEMENT_NODE ) {
                if( node.getNodeName().equals( tag ) ) {
                    return (Element) node;
                }
            }
        }

        throw new DOMException( DOMError.SEVERITY_WARNING, "Could not GetElement " + tag );
    }

    public List<Element> getElements( Element e ) {
        NodeList list = e.getChildNodes();

        List<Element> result = new ArrayList<Element>();

        for( int i = 0; i < list.getLength(); i++ ) {
            Node node = list.item( i );

            if( node.getNodeType() == Node.ELEMENT_NODE ) {
                Element e1 = (Element) node;
                result.add( e1 );
            }
        }

        return result;
    }
}
