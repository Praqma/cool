package net.praqma.clearcase.ucm.persistence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.ucm.UCMException;
import net.praqma.clearcase.ucm.entities.UCMEntity;
import net.praqma.utils.Debug;
import net.praqma.utils.Tuple;


public class UCMStrategyCleartool implements UCMStrategyInterface
{
	private static Debug logger = Debug.GetLogger();
	
	protected static final String rx_view_uuid  = "view_uuid:(.*)";
	private static final String rx_ccdef_allowed = "[\\w\\.-_\\\\]";
	
	static
	{
		//logger.ExcludeClass( UCMStrategyXML.class.getName() );
	}
	
	
	private static final String filesep = System.getProperty( "file.separator" );
	private static final String linesep = System.getProperty( "line.separator" );
	
	public UCMStrategyCleartool()
	{
		logger.log( "Using CLEARTOOL strategy" );
	}
	
	@Override
	public String LoadBaseline( String baseline )
	{
		String cmd = "desc -fmt %n" + delim + "%[component]p" + delim + "%[bl_stream]p" + delim + "%[plevel]p" + delim + "%u " + baseline;
		return Cleartool.run_collapse( cmd );
	}
	@Override
	public List<String> GetBaselineDiff( String baseline, String other, boolean nmerge )
	{
		// cleartool('diffbl -pre -act -ver '.$sw_nmerge.$self->get_fqname );
		String cmd = "diffbl -pre -act -ver -nmerge " + baseline;
		return Cleartool.run( cmd );
	}
	@Override
	public String SetPromotionLevel( String baseline, String plevel )
	{
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String GetBaselineActivities( String baseline )
	{
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public List<String> GetBaselines( String component, String stream, String plevel )
	{
		// my @retval = cleartool_qx(' lsbl -s -component '.$self->get_fqname().' -stream '.$stream->get_fqname().' -level '.$params{'plevel'});
		String cmd = "lsbl -s -component " + component + " -stream " + stream + " -level " + plevel;
		return Cleartool.run( cmd );
	}
	@Override
	public void RecommendBaseline( String stream, String baseline ) throws UCMException
	{
		// TODO Auto-generated method stub
		
	}
	@Override
	public String GetRecommendedBaselines( String stream )
	{
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String GetVersion( String version, String separator )
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	/*
wolles_baseline_02.6448
  Hyperlinks:
    tag@377@\Cool_PVOB ->  "test"
    tag@394@\Cool_PVOB ->  "test nummer 2"
    
    describe -ahlink tag -l
	 */
	
	private static final Pattern pattern_tags = Pattern.compile( "^\\s*(tag@\\d+@" + rx_ccdef_allowed + "+)\\s*->\\s*\"(.*?)\"\\s*$" );
	
	@Override
	public List<Tuple<String, String>> GetTags( String fqname )
	{
		String cmd = "describe -ahlink tag -l " + fqname;
		List<String> list = Cleartool.run( cmd );
		
		List<Tuple<String, String>> tags = new ArrayList<Tuple<String, String>>();
				
		/* There are tags */
		if( list.size() > 2 )
		{
			for( int i = 2 ; i < list.size() ; i++ )
			{
				logger.debug( "["+i+"]" + list.get( i ) );
				Matcher match = pattern_tags.matcher( list.get( i ) );
				if( match.find() )
				{
					tags.add( new Tuple<String, String>( match.group( 1 ), match.group( 2 ) ) );
				}
			}
		}
		
		return tags;
	}
	
	@Override
	public String GetTag( String fqname )
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	private static final Pattern pattern_remove_verbose_tag = Pattern.compile( "^.*?\"(.*)\".*?$" );
	
	@Override
	public String NewTag( UCMEntity entity, String cgi )
	{
		logger.trace_function();
		logger.debug( entity.GetFQName() );
		
		// mkhlink -ttext "test nummer 2" tag baseline:wolles_baseline_02.6448@\Cool_PVOB
		String cmd = "mkhlink -ttext \"" + cgi + "\" tag " + entity.GetFQName();
		String tag = Cleartool.run_collapse( cmd );
		Matcher match = pattern_remove_verbose_tag.matcher( tag );
		if( match.find() )
		{
			return match.group( 1 );
		}
		
		return "";
	}
	
	@Override
	public void DeleteTag( String fqname )
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void DeleteTagsWithID( String tagType, String tagID, String entity )
	{
		logger.trace_function();
		logger.debug( tagType + tagID );
		
		List<Tuple<String, String>> list = GetTags( entity );
		logger.debug( list.size() + " Tags!" );
		
		for( Tuple<String, String> t : list )
		{
			logger.debug( "Testing " + t.t1 + " > " + t.t2 );
			if( t.t2.matches( "^.*tagtype=" + tagType + ".*$" ) && t.t2.matches( "^.*tagid=" + tagID + ".*$" ) )
			{
				String cmd = "rmhlink " + t.t1;
				Cleartool.run( cmd );
			}
		}
		
	}
	@Override
	public String PutTag( String fqname, String keyval, UCMEntity entity )
	{
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void MakeSnapshotView( String stream, File viewroot, String viewtag )
	{
		// TODO Auto-generated method stub
		
	}
	@Override
	public String GetXML()
	{
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void SaveState()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public String GetStreamFromView( String viewtag )
	{
		String fqstreamstr =  Cleartool.run_collapse( "lsstream -fmt %Xn -view " + viewtag );

		return fqstreamstr;
	}

	@Override
	public File GetCurrentViewRoot( File viewroot )
	{
		String cwd = System.getProperty( "user.dir" );
		
		if( !cwd.equals( viewroot.toString() ) )
		{
			/* Experimental!!! */
			System.setProperty( "user.dir", viewroot.toString() );
		}
		
		String wvroot = Cleartool.run_collapse( "pwv -root" );

		/* Still experimental!!! */
		System.setProperty( "user.dir", cwd );
		
		return new File( wvroot );
	}
	
	public String ViewrootIsValid( File viewroot ) throws IOException
	{
		logger.debug( "UNTESTED CODE" );
		//viewroot. 
		//String viewdotdatpname = viewroot + filesep + "view.dat";
		File viewdotdatpname = new File( viewroot, "view.dat" );
		
		logger.debug( "The view file = " + viewdotdatpname );
		
		FileReader fr = null;
		try
		{
			fr = new FileReader( viewdotdatpname );
		}
		catch ( FileNotFoundException e1 )
		{
			logger.warning( "\"" + viewdotdatpname + "\" not found!" );
			throw new IOException( e1.getMessage() );
		}
		
		BufferedReader br = new BufferedReader( fr );
		String line;
		StringBuffer result = new StringBuffer();
		try
		{
			while( ( line = br.readLine() ) != null )
			{
				result.append( line );
			}
		}
		catch ( IOException e )
		{
			logger.warning( "Couldn't read lines from " + viewdotdatpname );
			throw e;
		}
		
		Pattern pattern = Pattern.compile( rx_view_uuid );
		Matcher match   = pattern.matcher( result.toString() );
		
		/* A match is found */
		String uuid = "";
		try
		{
			uuid = match.group( 1 ).trim();
		}
		catch( IllegalStateException e )
		{
			logger.log( "UUID not found!", "warning" );
			throw new UCMException( "UUID not found" );
		}
		
		//my $viewtag = cleartool("lsview -s -uuid $1");
		String cmd = "lsview -s -uuid " + uuid;
		String viewtag = Cleartool.run_collapse( cmd ).trim();
		
		return viewtag;
	}

	@Override
	public void CreateStream( String pstream, String nstream, boolean readonly )
	{
		// TODO Auto-generated method stub
		
	}
}