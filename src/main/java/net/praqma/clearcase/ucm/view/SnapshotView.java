package net.praqma.clearcase.ucm.view;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.*;
import net.praqma.clearcase.Cool;
import net.praqma.clearcase.PVob;
import net.praqma.clearcase.Vob;
import net.praqma.clearcase.api.ListVob;
import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.exceptions.CleartoolException;
import net.praqma.clearcase.exceptions.UnableToInitializeEntityException;
import net.praqma.clearcase.exceptions.UnableToListViewsException;
import net.praqma.clearcase.exceptions.UnableToLoadEntityException;
import net.praqma.clearcase.exceptions.ViewException;
import net.praqma.clearcase.exceptions.ViewException.Type;
import net.praqma.clearcase.ucm.entities.Stream;
import net.praqma.util.execute.AbnormalProcessTerminationException;
import net.praqma.util.execute.CommandLineInterface.OperatingSystem;
import net.praqma.util.io.IO;
import net.praqma.util.structure.Tuple;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;

/**
 * @author wolfgang
 */
@SuppressFBWarnings("")
public class SnapshotView extends UCMView {

	transient private static final Logger logger = Logger.getLogger( SnapshotView.class.getName() );

	protected static final Pattern rx_view_uuid_file = Pattern.compile( "view_uuid:(.*)" );
	protected static final Pattern rx_view_uuid = Pattern.compile( "View uuid:(.*)" );
	public static final Pattern rx_view_rebasing = Pattern.compile( "^\\.*Error: This view is currently being used to rebase stream \"(.+)\"\\.*$" );
	public static final Pattern pattern_cache = Pattern.compile( "^\\s*log has been written to\\s*\"(.*?)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE );
    public static final Pattern pattern_catcs = Pattern.compile("(.*=)(\\S+)\\](\\S+)(\\.\\.\\.\" )(\\S+)(.*)");    
	
	public static final String rx_co_file = ".*CHECKEDOUT$";
    public static final String rx_ctr_file = ".*\\.contrib";
    public static final String rx_keep_file = ".*\\.keep$";
    public static final String rx_updt_file = ".updt";

	
	private static String VIEW_DOT_DAT_FILE = "view.dat";
	
	static {
		if( Cool.getOS().equals( OperatingSystem.UNIX ) ) {
			VIEW_DOT_DAT_FILE = ".view.dat";
		}
	}
		 
	private File viewroot = null;
    private List<String> readOnlyLoadLines = new ArrayList<String>();
    private List<String> allLoadLines = new ArrayList<String>();
	private PVob pvob;
	private String uuid = "";
	private String globalPath = "";
	private Stream stream;

	public enum Components {
		ALL, MODIFIABLE
	}
    
    public static List<String> catcs(File viewRoot) {
        List<String> configLines = Cleartool.run("catcs", viewRoot).stdoutList;
        return configLines;
    }
    
    public static HashMap<String, Boolean> getAllLoadStrings(List<String> consoleIn) {
        
        HashMap<String, Boolean> rootFolders = new HashMap<String, Boolean>();
            
        for(String s : consoleIn) {
            if(!s.startsWith("element")) {
                continue;
            }

            Matcher m = pattern_catcs.matcher(s);
            if(m.matches()) {
                try {
                    String key = m.group(2) + m.group(3);
                    //remove the leading backward slash from vobtag and remove the leftover forward slash from the path
                    key = key.substring(1, key.length()-1);
                    
                    if(SystemUtils.IS_OS_WINDOWS) {
                        key = key.replace("/", "\\");
                    }                    
                    Boolean readOnly = s.contains("-nocheckout");
                    logger.info(String.format("Config spec line: %s  read-only = %s", key, readOnly));
                    rootFolders.put(key, readOnly);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Error in determining config spec for line: \n "+s ,ex);
                }
            }                 
        }

        return rootFolders;
    }

    public static HashMap<String, Boolean> getAllLoadStrings(File viewRoot) {
        List<String> consoleInput = SnapshotView.catcs(viewRoot);
        return SnapshotView.getAllLoadStrings(consoleInput);
    }
    
    public static boolean isSpecialFile(String file) {
        return ( file.matches( rx_co_file ) || file.matches( rx_keep_file ) || file.matches( rx_ctr_file ) || file.endsWith( rx_updt_file ) );
    }
    
    public static class ViewPrivateFileFilter implements FileFilter {

        @Override
        public boolean accept(File file) {
            return file.isFile() && !SnapshotView.isSpecialFile(file.getName()) && file.canWrite() && !file.getName().equals( VIEW_DOT_DAT_FILE );
        }        
    }
    
    public static class LoadRules2 {
		private String loadRules;
        private Components components;

        public LoadRules2() { 
            this.components = Components.ALL;
        }
        
        /*
		 * Create load rules based on {@link Components}
         * @param components The components to load
		 * @throws UnableToLoadEntityException Thrown on ClearTool error
         * @throws UnableToInitializeEntityException Thrown on ClearTool error
		 * @throws CleartoolException Thrown on ClearTool error
		 */
		public LoadRules2(Components components ) throws UnableToInitializeEntityException, CleartoolException, UnableToLoadEntityException {
            this.components = components;
		}
                        
        public List<String> getConsoleOutput(SnapshotView view) {
            List<String> configLines = Cleartool.run("catcs", view.getViewRoot()).stdoutList;
            return configLines;
        }        
        
        public String loadRuleSequence( SnapshotView view ) {
            String loadRuleSequence = "";           
            HashMap<String, Boolean> all = SnapshotView.getAllLoadStrings(getConsoleOutput(view));

			if( components.equals( Components.ALL ) ) {
				logger.info("All components - "+all.keySet() );
                loadRuleSequence = StringUtils.join(all.keySet(), " ");
			} else {
				logger.info("Modifiable components" );
                HashMap<String, Boolean> modifiables = getModifiableOnly(all);
                loadRuleSequence = StringUtils.join(modifiables.keySet(), " ");
			}
            
            //We should set the view properties here
            view.setReadOnlyLoadLines(getOnlyReadOnly(all));
            
            return loadRuleSequence;
        }
               
        public LoadRules2 apply(SnapshotView view) {
            String appValue = " -add_loadrules " + loadRuleSequence(view);
			this.loadRules = appValue;
            return this;
        }
        
        private List<String> getOnlyReadOnly(HashMap<String, Boolean> rootFolders) {
            List<String> readOnly = new ArrayList<String>();
            for(String key : rootFolders.keySet()) {
                if(rootFolders.get(key)) {
                    readOnly.add(key);
                }
            }            
            return readOnly;
        }

        private HashMap<String, Boolean> getModifiableOnly(HashMap<String, Boolean> rootFolders) {
            HashMap<String, Boolean> modifiable = new HashMap<String, Boolean>();
            for(String key : rootFolders.keySet()) {
                if(!rootFolders.get(key)) {
                    modifiable.put(key, rootFolders.get(key));
                }
            }            
            return modifiable;
        }

		/**
		 * Create load rules based on a string
		 * 
		 * @param loadRules The load rule specification
		 */
		public LoadRules2( String loadRules ) {
			this.loadRules = " -add_loadrules " + loadRules;
		}

		public String getLoadRules() {
			return loadRules;
		}
	}

	public SnapshotView() { }

	public SnapshotView( File viewroot ) throws UnableToInitializeEntityException, CleartoolException, ViewException, IOException {
		this.viewroot = viewroot;
		Tuple<Stream, String> t = getStreamFromView( viewroot );
		this.viewtag = t.t2;
		this.viewroot = viewroot;
		this.stream = t.t1;
		this.pvob = this.stream.getPVob();
	}

	/**
	 * Create a Snapshot view given a Stream, view root and a view tag.
	 * 
	 * @param stream
	 *            The Stream
	 * @param viewroot The root of this new view
	 * @param viewtag The tag to use
	 * @return SnapShotView The {@link SnapshotView} as a result of the creation
     * @throws net.praqma.clearcase.exceptions.ViewException  Thrown on ClearTool error, view cannot be created
     * @throws net.praqma.clearcase.exceptions.UnableToInitializeEntityException Thrown when a ClearTool error occurs
     * @throws net.praqma.clearcase.exceptions.CleartoolException Thrown on ClearTool error
     * @throws java.io.IOException File errors/other external non ClearTool errors
	 */
	public static SnapshotView create( Stream stream, File viewroot, String viewtag ) throws ViewException, UnableToInitializeEntityException, CleartoolException, IOException {
		logger.fine( "The view \"" + viewtag + "\" in \"" + viewroot + "\"" );

		if( viewroot.exists() ) {
			IO.deleteDirectory( viewroot );
		}

		stream.generate();

		String cmd = "mkview -snapshot -stgloc -auto -tag " + viewtag + " -stream " + stream + " \"" + viewroot.getAbsolutePath() + "\"";

		try {
			Cleartool.run( cmd );
		} catch( AbnormalProcessTerminationException e ) {
			logger.warning( "Could not create snapshot view \"" + viewtag + "\"" );
			throw new ViewException( "Unable to create view " + viewtag + " at " + viewroot, viewroot.getAbsolutePath(), Type.CREATION_FAILED, e );
		}

		SnapshotView view = new SnapshotView( viewroot );
		view.setStream( stream );
		
		return view;
	}

	public static void createEnvironment( File viewroot ) {
		createEnvironment( viewroot, "" );
	}

	public static void createEnvironment( File viewroot, String viewtagsuffix ) {
		String viewtag = "cool_" + System.getenv( "COMPUTERNAME" ) + "_env" + viewtagsuffix;
	}

	public static void regenerateViewDotDat( File dir, String viewtag ) throws IOException, UnableToListViewsException {
		logger.fine( dir + ", " + viewtag );

		File viewdat = new File( dir + File.separator + VIEW_DOT_DAT_FILE );

		if( viewdat.exists() ) {
			throw new IOException( VIEW_DOT_DAT_FILE + " file already exist. No need for regenerating." );
		}

		String cmd = "lsview -l " + viewtag;
		/* TODO Check this functions behavior, if the view doesn't exist */
		String result = "";
		try {
			result = Cleartool.run( cmd ).stdoutBuffer.toString();
		} catch( AbnormalProcessTerminationException e ) {
			throw new UnableToListViewsException( viewtag, dir, e );
		}

		Matcher match = rx_view_uuid.matcher( result );
		if( !match.find() ) {
			logger.warning( "The UUID of the view " + viewtag + " does not exist!" );
			throw new IOException( "The UUID of the view " + viewtag + " does not exist!" );
		}

		String uuid = match.group( 1 );

		cmd = "lsview -uuid " + uuid;

		try {
			Cleartool.run( cmd );
		} catch( AbnormalProcessTerminationException e ) {
			throw new IOException( "Unable to read the UUID(" + uuid + ") from view tag " + viewtag, e );
		}

		if( dir.exists() ) {
			logger.warning( "The view root, " + dir + ",  already exists - reuse may be problematic" );
		} else {
			dir.mkdirs();
		}

		try {
			FileOutputStream fos = new FileOutputStream( viewdat );
			fos.write( ( "ws_oid:00000000000000000000000000000000 view_uuid:" + uuid ).getBytes() );
			fos.close();
		} catch( IOException e ) {
			throw new IOException( "Could not create " + VIEW_DOT_DAT_FILE, e );
		}

		if( !viewdat.setReadOnly() ) {
			logger.warning( "Could not set " + VIEW_DOT_DAT_FILE + " as read only" );
			throw new IOException( "Could not set " + VIEW_DOT_DAT_FILE + " as read only" );
		}
	}
    
    public List<String> getReadOnlyLoadLines() {
        return readOnlyLoadLines;
    }
    
    public void setReadOnlyLoadLines(List<String> readOnlyLoadLines) {
        this.readOnlyLoadLines = readOnlyLoadLines;
    }
    
    public List<String> getAllLoadLines() {
        return allLoadLines;
    }
    
    public void setAllLoadLines(List<String> allLoadLines) {
        this.allLoadLines = allLoadLines;
    }

	public File getViewRoot() {
		return this.viewroot;
	}

	@Override
	public String getPath() {
		return this.viewroot.toString();
	}

    @Override
	public Stream getStream() throws UnableToInitializeEntityException, CleartoolException, ViewException, IOException {
		if( this.stream == null ) {
			this.stream = getStreamFromView( getViewRoot() ).getFirst();
		}
		return stream;
	}

	private void setStream( Stream stream ) {
		this.stream = stream;
	}

	public static String getViewtag( File context ) throws CleartoolException {
		String cmd = "pwv -s";
		try {
			return Cleartool.run( cmd, context ).stdoutBuffer.toString();
		} catch( AbnormalProcessTerminationException e ) {
			throw new CleartoolException( "Unable to get view tag at " + context , e );
		}
	}

	public static SnapshotView getSnapshotViewFromPath( File viewroot ) throws ClearCaseException, IOException {
		String viewtag = getViewtag( viewroot );
		SnapshotView view = null;

		if( UCMView.viewExists( viewtag ) ) {
			view = get( viewroot );
		} else {
			throw new ClearCaseException( "View is not valid" );
		}

		return view;
	}

	/**
	 * Determine if the views view root is valid, returning its view tag
	 * 
     * @param viewroot The view root ({@link File})
	 * @return The view tag
	 * @throws IOException Thrown when the filesystem cannot find the files specified 
	 * @throws CleartoolException Thrown on ClearTool error 
	 * @throws ViewException  Thrown on ClearTool error, the view was not created
	 */
	public static String viewrootIsValid( File viewroot ) throws IOException, CleartoolException, ViewException {
		logger.fine( viewroot.getAbsolutePath() );

		File viewdotdatpname = new File( viewroot + File.separator + VIEW_DOT_DAT_FILE );

		logger.fine( "The view file = " + viewdotdatpname );

		FileReader fr = null;
		try {
			fr = new FileReader( viewdotdatpname );
		} catch( FileNotFoundException e1 ) {
			logger.warning( "\"" + viewdotdatpname + "\" not found!" );
			throw new ViewException( "No view .dat file found", viewroot.getAbsolutePath(), Type.VIEW_DOT_DAT, e1 );
		}

		BufferedReader br = new BufferedReader( fr );
		String line;
		StringBuffer result = new StringBuffer();
		try {
			while( ( line = br.readLine() ) != null ) {
				result.append( line );
			}
		} catch( IOException e ) {
			logger.warning( "Couldn't read lines from " + viewdotdatpname );
			throw e;
		}

		Matcher match = rx_view_uuid_file.matcher( result.toString() );

		String uuid = "";

		if( match.find() ) {
			/* A match is found */
			uuid = match.group( 1 ).trim();
		} else {
			logger.warning( "UUID not found!" );
			throw new IOException( "UUID not found" );
		}

		String cmd = "lsview -s -uuid " + uuid;
		try {
			String viewtag = Cleartool.run( cmd ).stdoutBuffer.toString().trim();
			return viewtag;
		} catch( AbnormalProcessTerminationException e ) {
			throw new CleartoolException( "Unable to list view with " + uuid, e );
		}
	}
    
	public class UpdateInfo {
		public Integer totalFilesToBeDeleted = 0;
		public boolean success = false;
		public Integer filesDeleted = 0;
		public Integer dirsDeleted = 0;
	}

	public final Tuple<Stream, String> getStreamFromView( File vr ) throws UnableToInitializeEntityException, CleartoolException, ViewException, IOException {
		File wvroot = getCurrentViewRoot( vr );
		String vt = viewrootIsValid( wvroot );
		String streamstr = getStreamFromView( vt );
		Stream sstr = Stream.get( streamstr );
		return new Tuple<Stream, String>( sstr, vt );
	}

	public File getCurrentViewRoot( File viewroot ) throws ViewException {
		logger.fine( viewroot.getAbsolutePath() );

		try {
			String wvroot = Cleartool.run( "pwv -root", viewroot ).stdoutBuffer.toString();

			return new File( wvroot );
		} catch( Exception e ) {
			throw new ViewException( "Unable to get current view " + viewroot, path, Type.INFO_FAILED, e );
		}
	}

	public String getStreamFromView( String viewtag ) throws ViewException {
		try {
			String fqstreamstr = Cleartool.run( "lsstream -fmt %Xn -view " + viewtag ).stdoutBuffer.toString();
			return fqstreamstr;
		} catch( AbnormalProcessTerminationException e ) {
			throw new ViewException( "Unable to get stream from view " + viewtag, path, Type.INFO_FAILED, e );
		}
	}
    
    /**
     * TODO: This one should be used for new method of updating
     * @param swipe Swipe
     * @param generate Generated
     * @param overwrite Overview
     * @param excludeRoot Exclude root
     * @param loadRules Load rules
     * @return An {@link UpdateInfo} 
     * @throws net.praqma.clearcase.exceptions.CleartoolException Thrown on ClearTool error 
     * @throws net.praqma.clearcase.exceptions.ViewException  Thrown on ClearTool error
     */
    public UpdateInfo update( boolean swipe, boolean generate, boolean overwrite, boolean excludeRoot, LoadRules2 loadRules ) throws CleartoolException, ViewException {

		UpdateInfo info = new UpdateInfo();

		if( generate ) {
			this.stream.generate();
		}

		logger.fine( "STREAM GENERATES" );

		if( swipe ) {
			Map<String, Integer> sinfo = swipe( this.viewroot, excludeRoot, loadRules.getLoadRules() );
			info.success = sinfo.get( "success" ) == 1 ? true : false;
			info.totalFilesToBeDeleted = sinfo.get( "total" );
			info.dirsDeleted = sinfo.get( "dirs_deleted" );
			info.filesDeleted = sinfo.get( "files_deleted" );
		}

		logger.fine( "SWIPED" );

		// Cache current directory and chdir into the viewroot
		String result = updateView( this, overwrite, loadRules.getLoadRules() );        
		logger.fine( result );

		return info;
	}

	private static String updateView( SnapshotView view, boolean overwrite, String loadrules ) throws CleartoolException, ViewException {
		String result = "";
		
		logger.fine( view.getViewRoot().getAbsolutePath() );

		String cmd = "setcs -stream";
		try {
			Cleartool.run( cmd, view.getViewRoot(), false );
		} catch( AbnormalProcessTerminationException e ) {
			throw new CleartoolException( "Unable to set cs stream: " + view.getViewRoot() , e );
		}

		logger.fine( "Updating view" );

		cmd = "update -force " + ( overwrite ? " -overwrite " : "" ) + loadrules;
		try {
			result = Cleartool.run( cmd, view.getViewRoot(), true ).stdoutBuffer.toString();
		} catch( AbnormalProcessTerminationException e ) {
			Matcher m = rx_view_rebasing.matcher( e.getMessage() );
			if( m.find() ) {
				logger.log( Level.WARNING, "The view is currently rebasing the stream" + m.group( 1 ), e);
				throw new ViewException( "The view is currently rebasing the stream " + m.group( 1 ), view.getViewRoot().getAbsolutePath(), Type.REBASING, e );
			} else {
                logger.log( Level.WARNING, "Unable to update view", e );
				throw new ViewException( "Unable to update view", view.getViewRoot().getAbsolutePath(), Type.UNKNOWN, e );
			}
		}

		
		Matcher match = pattern_cache.matcher( result );
		if( match.find() ) {
			return match.group( 1 );
		}

		return "";
	}
    
    public Map<String, Integer> swipe ( File viewroot, boolean excludeRoot ) throws CleartoolException {
        return swipe(viewroot, excludeRoot, null);
    }

	public Map<String, Integer> swipe( File viewroot, boolean excludeRoot, String loadrules) throws CleartoolException {
		logger.fine( viewroot.toString() );

		File[] files = viewroot.listFiles();
		List<File> notVobs = new ArrayList<File>();
		List<File> rootVPFiles = new ArrayList<File>();
        List<File> vobfolders = new LinkedList<File>(  );

		/*
		 * Scanning root folder for directories that are not vobs and files, not
		 * view.dat
		 */
		for( File f : files ) {
			if( !f.canWrite() ) {
				logger.fine( f + " is write protected." );
				continue;
			}

			if( f.isDirectory() ) {
                //TODO: The clearcase functionality should be removed once swipe 2.0 is ready. We should only use the one that checks with loadrules.
                if(loadrules == null) {
                    if( Vob.isVob( f ) ) {
                        vobfolders.add( f );
                    } else {
                        notVobs.add( f );
                    } 
                } else {
                    logger.info( String.format( "The loadrule: %s%nContains %s", loadrules, f.getName() ) );
                    if(loadrules.contains(f.getName())) {
                        vobfolders.add(f);
                    } else {
                        notVobs.add(f);
                    }
                } 
			} else {
				if( f.getName().equalsIgnoreCase( VIEW_DOT_DAT_FILE ) ) {
					continue;
				}
                if( !SnapshotView.isSpecialFile( f.getName() ) ) {
                    rootVPFiles.add( f );
                }
			}
		}

		/* Remove all other dirs */
		for( File notVob : notVobs ) {
			logger.fine( "Removing " + notVob );
			net.praqma.util.io.IO.deleteDirectory( notVob );
		}

		Map<String, Integer> info = new HashMap<String, Integer>();
		info.put( "success", 1 );
        info.put( "total", 0 );
        info.put( "dirs_deleted", 0 );
        info.put( "files_deleted", 0 );

        logger.fine( "Finding view private files" );
        List<File> vpFiles = new ArrayList<File>();

        for( File folder : vobfolders ) {
            logger.fine( "Finding view private files for " + folder );
            //TODO: Once swipe 2.0 has been verified, this check needs to be removed, we only need to use the findViewPrivateFilesFromVobUsingFileFilter method.
            if(loadrules == null) {
                vpFiles.addAll( findViewPrivateFilesFromVob( folder ) );
            } else {
                vpFiles.addAll( findViewPrivateFilesFromVobUsingFileFilter( folder ));
            }
        }

        if( !excludeRoot ) {
            vpFiles.addAll( rootVPFiles );
        }

		int total = vpFiles.size();
        logger.finest( "Aggregated view private files: " + vpFiles );

        if( total == 0 ) {
            logger.fine( "No files to delete" );
            return info;
        }

		logger.fine( "Found " + total + " files, of which " + ( total - vpFiles.size() ) + " were UPDT, CO, CTR or KEEP's." );

		List<File> dirs = new ArrayList<File>();
		int dircount = 0;
		int filecount = 0;

		/* Removing view private files, saving directories for later */
		logger.fine( "Removing files" );
		for( File f : vpFiles ) {
			if( f.exists() ) {
				if( f.isDirectory() ) {
                    /* TODO The directory could just be recursively deleted?! All sub files are view private too as well. */
					dirs.add( f );
				} else {
                    if(	f.delete() ) {
                        logger.finest(String.format( "Deleted file: %s", f.getAbsolutePath() ));
					    filecount++;
                    } else {
                        logger.warning( "Could not delete " + f );
                    }
				}
			} else {
				logger.fine( "The file " + f + " does not exist." );
			}
		}

		info.put( "files_deleted", filecount );

		/* TODO Remove the directories, somehow!? Only the empty!? */
		logger.config( "Removing directories:" );
		for( File d : dirs ) {
			try {
                if(	d.delete() ) {
                    dircount++;
                } else {
                    logger.warning( "Could not delete " + d );
                }
			} catch( SecurityException e ) {
				logger.fine( "Unable to delete \"" + d + "\". Probably not empty." );
			}
		}

		info.put( "dirs_deleted", dircount );

		logger.fine( "Deleted " + dircount + " director" + ( dircount == 1 ? "y" : "ies" ) + " and " + filecount + " file" + ( filecount == 1 ? "" : "s" ) );

		if( dircount + filecount == total ) {
			info.put( "success", 1 );
		} else {
			logger.warning( "Some files were not deleted." );
			info.put( "success", 0 );
		}

		return info;
	}

    /**
     * Returns a list of view private files from a {@link Vob} folder.
     * @param vobFolder The {@link File} path for the {@link Vob}
     * @return A {@link List} of {@link File}s representing the view private files of a vob in the view.
     * @throws CleartoolException Thrown on ClearTool error
     */
    private List<File> findViewPrivateFilesFromVob( File vobFolder ) throws CleartoolException {
        List<String> result = new ListVob().recurse().restrictToViewOnly().shortReportLength().addPathName( vobFolder.getAbsolutePath() ).execute();

        logger.finest( "View private files for " + vobFolder + ": " + result );

        List<File> vpFiles = new ArrayList<File>( result.size() );

        for( String vpFile : result ) {
            if( SnapshotView.isSpecialFile( vpFile ) ) {
                continue;
            }
            vpFiles.add( new File( vpFile ) );
        }

        return vpFiles;
    }

    /**
     * Filters files in any given VOB folder, based on the assumption that all files that are writable (Not-read-only)
     * in a given vob folder are are view-private and can be safely deleted. We also exclude all the special cases like view update
     * and other files.
     * @param vobFolder The vob to look for view private files
     * @return A list of view private {@link File}s 
     */
    private List<File> findViewPrivateFilesFromVobUsingFileFilter( File vobFolder ) {
        ArrayList<File> foldersToCheck = new ArrayList<File>();
        findAllSubDirs(vobFolder, foldersToCheck);
        
        ViewPrivateFileFilter roff = new ViewPrivateFileFilter();
        List<File> files = new ArrayList<File>(Arrays.asList(vobFolder.listFiles(roff)));
        for(File f : foldersToCheck) {
            files.addAll(Arrays.asList(f.listFiles(roff)));
        }
        
        
        logger.info(String.format( "Found %s view private files in vob folder %s", files.size(), vobFolder.getName() ));
        return files;
    }
    
    private List<File> findAllSubDirs(File rootFolder, List<File> folders) {
        File[] allfiles = rootFolder.listFiles();
        for (File file : allfiles) {
            if (file.isDirectory()) {
                folders.add(file);
                findAllSubDirs(file, folders);
            }
        }
        return folders;
    }
    
    public Map<String, Integer> swipe( boolean excludeRoot, String loadRules ) throws CleartoolException {
		logger.fine( "Swiping " + this.getViewRoot() );
		Map<String, Integer> sinfo = swipe( viewroot, excludeRoot, loadRules );
		return sinfo;
	}
	
	public static SnapshotView get( File viewroot ) throws IOException, ViewException, UnableToInitializeEntityException, CleartoolException {
		String viewtag = getViewtag( viewroot );
		SnapshotView view = null;

		if( UCMView.viewExists( viewtag ) ) {
			view = new SnapshotView( viewroot );
		} else {
			throw new ViewException( "View is not valid", viewroot.getAbsolutePath(), Type.DOES_NOT_EXIST );
		}

		return view;
	}
}
