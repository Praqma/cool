package net.praqma.clearcase.test.functional;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.ClassRule;
import org.junit.Test;

import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.test.junit.ClearCaseRule;
import net.praqma.clearcase.ucm.entities.Baseline;
import net.praqma.clearcase.ucm.entities.Component;
import net.praqma.clearcase.ucm.entities.Project;
import net.praqma.clearcase.ucm.entities.Baseline.LabelBehaviour;
import net.praqma.clearcase.ucm.entities.Project.PromotionLevel;
import net.praqma.clearcase.ucm.entities.Stream;
import net.praqma.clearcase.util.ExceptionUtils;

public class TestStream {
	
	@ClassRule
	public static ClearCaseRule ccenv = new ClearCaseRule( "cool-stream" );

	@Test
	public void testFoundationBaselines() throws Exception {
		
		Stream stream = Stream.get( "one_int", ccenv.getPVob() ).load();
		
		System.out.println( "Foundation baselines:" + stream.getFoundationBaselines() );
		
		assertEquals( "_System_1.0", stream.getFoundationBaselines().get( 0 ).getShortname() );

		assertTrue( true );
	}
	
	@Test
	public void testCreateStream() throws Exception {
		
		Stream parent = ccenv.context.streams.get( "one_dev" );
		
		Stream nstream = Stream.create( parent, "new-stream", false, new ArrayList<Baseline>() );
		
		assertNotNull( nstream );
		assertEquals( "stream:new-stream@" + ccenv.getPVob(), nstream.getFullyQualifiedName() );
	}
	
	@Test
	public void testCreateIntegrationStream() throws Exception {
		
		Project project = Project.create( "test-project", null, ccenv.getPVob(), 0, null, true, new ArrayList<Component>() );
		
		Stream istream = Stream.createIntegration( "test-int", project, ccenv.context.baselines.get( "_System_1.0" ) );
		
		assertNotNull( istream );
		assertEquals( "stream:test-int@" + ccenv.getPVob(), istream.getFullyQualifiedName() );
		
		istream.load();
		
		assertEquals( istream.getFoundationBaseline(), ccenv.context.baselines.get( "_System_1.0" ) );
	}
	
	@Test
	public void testGetChildStreamsNoMultisite() throws Exception {
		
		Stream istream = Stream.get( "one_int", ccenv.getPVob() );
		
		List<Stream> childs = istream.getChildStreams( false );
		
		assertEquals( 2, childs.size() );		
	}
	
	@Test
	public void testGetPostedDelivery() throws Exception {
		
		Stream istream = Stream.get( "one_int", ccenv.getPVob() );
		
		List<Baseline> baselines = istream.getPostedBaselines( ccenv.context.components.get( 0 ), PromotionLevel.INITIAL );
		
		assertEquals( 0, baselines.size() );
	}
	
	
	@Test
	public void testHasPostedDelivery() throws Exception {
		
		Stream istream = Stream.get( "one_int", ccenv.getPVob() );
		
		boolean has = istream.hasPostedDelivery();
		
		assertFalse( has );
	}
	
	@Test
	public void testGetSiblingStream() throws Exception {
		
		Project project1 = Project.create( "test-project1", null, ccenv.getPVob(), 0, null, true, new ArrayList<Component>() );
		Stream istream1 = Stream.createIntegration( "test-int1", project1, ccenv.context.baselines.get( "_System_1.0" ) );
		project1.setStream( istream1 );
		
		Project project2 = Project.create( "test-project2", null, ccenv.getPVob(), 0, null, true, new ArrayList<Component>() );
		Stream istream2 = Stream.createIntegration( "test-int2", project2, ccenv.context.baselines.get( "_System_1.0" ) );
		
		istream1.setDefaultTarget( istream2 );
		
		List<Stream> siblings = istream2.getSiblingStreams();
		
		System.out.println( "SIBLINGS: " + siblings );
		
		assertEquals( 1, siblings.size() );
	}
	
	@Test
	public void testStreamExists() throws Exception {
		
		Stream istream = Stream.get( "one_int", ccenv.getPVob() );
		
		assertTrue( istream.exists() );		
	}
	
	@Test
	public void testGetRecommendedBaselines() throws Exception {
		
		Stream istream = Stream.get( "one_int", ccenv.getPVob() );
		
		List<Baseline> baselines = istream.getRecommendedBaselines();
		
		System.out.println( "RECOMMENDED BASELINES: " + baselines );
		
		assertEquals( 1, baselines.size() );
	}
	
	@Test
	public void testGenerate() throws Exception {
		
		Stream istream = Stream.get( "one_int", ccenv.getPVob() );
		
		istream.generate();
	}
	
	@Test
	public void testRecommendBaseline() throws Exception {
		
		String viewtag = ccenv.getUniqueName() + "_one_int";
		System.out.println( "VIEW: " + ccenv.context.views.get( viewtag ) );
		//File path = new File( context.views.get( viewtag ).getPath() );
		File path = new File( ccenv.context.mvfs + "/" + ccenv.getUniqueName() + "_one_int/" + ccenv.getVobName() );
		
		Stream stream = Stream.get( "one_int", ccenv.getPVob() );
		
		System.out.println( "PATH: " + path );
		
		try {
			ccenv.addNewContent( ccenv.context.components.get( "Model" ), path, "test.txt" );
		} catch( ClearCaseException e ) {
			ExceptionUtils.print( e, System.out, true );
		}
		
		Baseline rb = Baseline.create( "recommend-baseline", ccenv.context.components.get( "_System" ), path, LabelBehaviour.FULL, false );
		
		stream.recommendBaseline( rb );
	}
	
	
	@Test
	public void testLatestBaselines() throws Exception {
		
		String viewtag = ccenv.getUniqueName() + "_one_int";
		System.out.println( "VIEW: " + ccenv.context.views.get( viewtag ) );
		//File path = new File( context.views.get( viewtag ).getPath() );
		File path = new File( ccenv.context.mvfs + "/" + ccenv.getVobName() + "_one_int/" + ccenv.getVobName() );
		
		Stream stream = Stream.get( "one_int", ccenv.getPVob() );
		
		List<Baseline> latest = stream.getLatestBaselines();
		
		System.out.println( "Latest baselines: " + latest );
		
		assertEquals( 6, latest.size() );
	}

}
