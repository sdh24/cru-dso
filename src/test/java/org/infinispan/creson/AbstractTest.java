package org.infinispan.creson;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.creson.object.Reference;
import org.infinispan.creson.utils.*;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.marshall.core.JBossMarshaller;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.infinispan.creson.CresonModuleLifeCycle.CRESON_CACHE_NAME;
import static org.testng.Assert.assertTrue;

/**
 * @author Pierre Sutra
 */

@Test(testName = "AbstractTest", groups = "unit")
public abstract class AbstractTest extends MultipleCacheManagersTest {

   protected static Log log = LogFactory.getLog(AbstractTest.class);
   static{
      Configurator.setLevel(System.getProperty("log4j.logger"), Level.ERROR);
   }

   protected static final CacheMode CACHE_MODE = CacheMode.DIST_SYNC;
   protected static final int NCALLS = 10000;
   protected static final long MAX_ENTRIES = Integer.MAX_VALUE;
   protected static final int REPLICATION_FACTOR = 2;
   protected static final int NMANAGERS = 3;
   protected static final String PERSISTENT_STORAGE_DIR = "/tmp/creson-storage";

   protected ConfigurationBuilder buildConfiguration() {
      return ConfigurationHelper.buildConfiguration(
              CACHE_MODE,
              REPLICATION_FACTOR,
              MAX_ENTRIES,
              PERSISTENT_STORAGE_DIR + "/" + containers().size());
   }

   @Test(enabled = false)
   public void baseProperties() throws Exception {

      BasicCache cache = container(0).getCache();

      // 1 - validate cache atomicity
      Random rand = new Random();
      for(int i=0; i<1000; i++) {
         int k = rand.nextInt(10);
         int v = rand.nextInt();
         cache.put(k, v);
         assert cache.get(k).equals(v);
      }

   }

   @Test
   public void baseUsage() throws Exception {

      BasicCacheContainer cacheManager = containers().iterator().next();
      BasicCache<Object, Object> cache = cacheManager.getCache(CRESON_CACHE_NAME);
      Factory factory = Factory.forCache(cache);

      // 1 - basic call
      Set<String> set = factory.getInstanceOf(HashSet.class, "set");
      set.add("smthing");
      assert set.contains("smthing");
      assert set.size() == 1;

      // 2 - proxy marshalling
      Marshaller marshaller = new JBossMarshaller();
      assert marshaller.objectFromByteBuffer((marshaller.objectToByteBuffer(set))) instanceof Reference;

      factory.close();

   }

   @Test(enabled = true)
   public void basePerformance() throws Exception{

      BasicCacheContainer cacheManager = containers().iterator().next();
      BasicCache<Object, Object> cache = cacheManager.getCache(CRESON_CACHE_NAME);
      Factory factory = Factory.forCache(cache);

      int f = 1; // multiplicative factor

      Map map = factory.getInstanceOf(HashMap.class, "map");

      long start = System.currentTimeMillis();
      for (int i = 0; i < NCALLS * f; i++) {
         map.containsKey("1");
      }

      System.out.println("op/sec:" + ((float) (NCALLS * f)) / ((float) (System.currentTimeMillis() - start)) * 1000);

   }

   @Test
   public void persistence() throws Exception {

      assertTrue(containers().size() >= 2);

      Iterator<BasicCacheContainer> it = containers().iterator();

      BasicCacheContainer container1 = it.next();
      BasicCache<Object, Object> cache1 = container1.getCache(CRESON_CACHE_NAME);
      Factory factory1 = Factory.forCache(cache1);

      BasicCacheContainer container2 = it.next();
      BasicCache<Object, Object> cache2 = container2.getCache(CRESON_CACHE_NAME);
      Factory factory2 = Factory.forCache(cache2);

      HashSet set1, set2;

      // 0 - Base persistence
      set1 = factory1.getInstanceOf(HashSet.class, "persist1", false, true);
      set1.add("smthing");
      factory1.disposeInstanceOf(HashSet.class, "persist1");
      set1 = factory1.getInstanceOf(HashSet.class, "persist1", false, false);
      assert set1.contains("smthing");
      factory1.disposeInstanceOf(HashSet.class, "persist1");

      // 1 - Concurrent retrieval
      set1 = factory1.getInstanceOf(HashSet.class, "persist2");
      set1.add("smthing");
      set2 = factory2.getInstanceOf(HashSet.class, "persist2", false, false);
      assert set2.contains("smthing");
      factory1.disposeInstanceOf(HashSet.class, "persist2");
      factory2.disposeInstanceOf(HashSet.class, "persist2");

      // 2 - Serial storing then retrieval
      set1 = factory1.getInstanceOf(HashSet.class, "persist3");
      set1.add("smthing");
      factory1.disposeInstanceOf(HashSet.class, "persist3");
      set2 = factory2.getInstanceOf(HashSet.class, "persist3", false, false);
      assert set2.contains("smthing");
      factory1.disposeInstanceOf(HashSet.class, "persist3");
      factory2.disposeInstanceOf(HashSet.class, "persist3");

      // 3 - Re-creation
      set1 = factory1.getInstanceOf(HashSet.class, "persist4");
      set1.add("smthing");
      factory1.disposeInstanceOf(HashSet.class, "persist4");
      set2 = factory2.getInstanceOf(HashSet.class, "persist4", false, true);
      assert !set2.contains("smthing");
      factory2.disposeInstanceOf(HashSet.class, "persist4");

   }

   @Test
   public void baseReadOptimization() throws Exception {
      SimpleObject object = new SimpleObject("baseReadOptimization");
      object.setField("something");
      String field = object.getField();
      assert field.equals("something");
   }

   @Test
   public void advancedReadOptimization() throws Exception {

      SimpleObject object = new SimpleObject("performance");

      long start = System.currentTimeMillis();
      for(int i=0; i<NCALLS;i++){
         object.setField(Integer.toString(i));
      }
      System.out.println("op/sec:"+((float)(NCALLS))/((float)(System.currentTimeMillis() - start))*1000);

      start = System.currentTimeMillis();
      for(int i=0; i<NCALLS;i++){
         object.getField();
      }
      System.out.println("op/sec:" + ((float) (NCALLS)) / ((float) (System.currentTimeMillis() - start)) * 1000);

   }

   @Test
   public void baseCacheTest() throws Exception {

      Iterator<BasicCacheContainer> it = containers().iterator();
      BasicCacheContainer container1 = it.next();
      BasicCache<Object, Object> cache1 = container1.getCache(CRESON_CACHE_NAME);
      Factory factory1 = Factory.forCache(cache1, 1);

      HashSet set1, set2;

      // 0 - Base caching
      set1 = factory1.getInstanceOf(HashSet.class, "aset", false, true);
      set1.add("smthing");
      set2 = factory1.getInstanceOf(HashSet.class, "aset2", false, true);
      assert set1.contains("smthing");

      // 1 - Caching multiple instances of the same object
      set1 = factory1.getInstanceOf(HashSet.class, "aset3", false, true);
      set1.add("smthing");
      set2 = factory1.getInstanceOf(HashSet.class, "aset3", false, false);
      assert set1.contains("smthing");
      assert set2.contains("smthing");

   }

   @Test
   public void concurrentUpdate() throws Exception {

      ExecutorService service = Executors.newCachedThreadPool();
      List<Future<Integer>> futures = new ArrayList<>();

      for (BasicCacheContainer manager : containers()) {
         Set set = Factory.forCache(manager.getCache(CRESON_CACHE_NAME))
                 .getInstanceOf(HashSet.class,"concurrent");
         futures.add(service.submit(
                 new ExerciseAtomicSetTask(set, NCALLS)));
      }

      long start = System.currentTimeMillis();
      Integer total = 0;
      for (Future<Integer> future : futures) {
         total += future.get();
      }
      System.out.println("Average time: " + (System.currentTimeMillis() - start));

      assert total == (NCALLS) : "obtained = " + total + "; espected = " + (NCALLS);

   }

   @Test
   public void multipleCreation() throws Exception {

      assertTrue(containers().size() >= 2);

      Iterator<BasicCacheContainer> it = containers().iterator();

      BasicCacheContainer container1 = it.next();
      BasicCache<Object, Object> cache1 = container1.getCache(CRESON_CACHE_NAME);
      Factory factory1 = Factory.forCache(cache1);

      BasicCacheContainer container2 = it.next();
      BasicCache<Object, Object> cache2 = container2.getCache(CRESON_CACHE_NAME);
      Factory factory2 = Factory.forCache(cache2);

      int n = 100;
      for (int i = 0; i < n; i++) {
         ArrayList list = factory2.getInstanceOf(ArrayList.class, "list"+i);
         list.add(i);
      }

      for (int i = 0; i < n; i++) {
         ArrayList list = factory1.getInstanceOf(ArrayList.class, "list"+i);
         assert (list.get(0).equals(i)) : list.get(0);
      }

   }

   @Test
   public void baseAspectJ() throws Exception {

      // 1 - constructor
      SimpleObject object = new SimpleObject();
      String field = object.getField();
      assert field.equals("test");

      // 2 - constructor w. arguments
      SimpleObject object1 = new SimpleObject("test2");
      assert object1.getField().equals("test2");

      // 3 - equals()
      for(int i=0; i<100; i++) {
         AdvancedShardedObject advancedShardedObject = new AdvancedShardedObject(UUID.randomUUID());
         AdvancedShardedObject advancedShardedObject1 = advancedShardedObject.getSelf();
         assert advancedShardedObject.equals(advancedShardedObject1);
      }

   }

   @Test
   public void baseComposition() throws Exception {
      assert ShardedObject.class.isAssignableFrom(SimpleShardedObject.class);
      SimpleShardedObject object = new SimpleShardedObject();
      SimpleShardedObject object2 = new SimpleShardedObject(object);
      ShardedObject object3 = object2.getShard();
      assert object3.equals(object);

      List<SimpleObject> list = new ArrayList<>();
      Random random = new Random(System.currentTimeMillis());
      for(int i=0; i<10; i++) {
         list.add(new SimpleObject(Integer.toString(random.nextInt(10))));
      }
      for(SimpleObject simpleObject1 : list){
         for(SimpleObject simpleObject2 : list){
            if (simpleObject1.equals(simpleObject2))
               assert simpleObject1.getField().equals(simpleObject2.getField());
         }
      }

   }

   @Test
   public void advancedComposition() throws Exception {
      AdvancedShardedObject object1 = new AdvancedShardedObject(UUID.randomUUID());
      AdvancedShardedObject object2 = new AdvancedShardedObject(UUID.randomUUID(), object1);

      ShardedObject shard = object2.getShard();
      assert shard.equals(object1);
      assert object1.flipValue();
      assert !((AdvancedShardedObject) object2.getShard()).flipValue();
      assert object2.flipValue();

      // TODO improve tests on static fields
      List<AdvancedShardedObject> rlist = object2.getList();
      rlist.clear();
      object1.addSelf();
      assert rlist.get(0) instanceof AdvancedShardedObject;
      assert rlist.get(0).equals(object1) :  rlist.get(0);
   }

   @Test
   public void baseElasticity() throws Exception {

      advancedComposition();
      baseUsage();

      addContainer();
      persistence();
      advancedComposition();
      baseUsage();

      deleteContainer();
      baseUsage();
      advancedComposition();
   }

   @Test
   public void advancedElasticity() throws Exception {

      ExecutorService service = Executors.newCachedThreadPool();
      List<Future<Integer>> futures = new ArrayList<>();

      Set set = Factory.forCache(manager(0).getCache(CRESON_CACHE_NAME))
              .getInstanceOf(HashSet.class,"elastic");
      futures.add(service.submit(
              new ExerciseAtomicSetTask(set, NCALLS)));

      // elasticity
      Set<Future> completed = new HashSet<>();
      while (completed.size() != futures.size()) {
         Thread.sleep(3000);
         if (containers().size() == NMANAGERS) {
            addContainer();
         } else {
            deleteContainer();
         }
         for (Future<Integer> future : futures) {
            if (future.isDone())
               completed.add(future);
         }
      }

      Integer total = 0;
      for (Future<Integer> future : futures) {
         total += future.get();
      }

      assert total == (NCALLS) : "obtained = " + total + "; expected = " + (NCALLS);

   }

   //
   // Interface
   //

   public abstract BasicCacheContainer container(int i);

   public abstract Collection<BasicCacheContainer> containers();

   public abstract boolean addContainer();

   public abstract boolean deleteContainer();

   //
   // Helpers
   //

   @Override
   @AfterClass(alwaysRun = true)
   protected void destroy() {
      Factory factory;
      for (EmbeddedCacheManager manager : cacheManagers) {
         factory = Factory.forCache(manager.getCache(CRESON_CACHE_NAME));
         if (factory!=null) factory.close();
      }
      for (BasicCacheContainer container : containers()) {
         factory = Factory.forCache(container.getCache(CRESON_CACHE_NAME));
         factory.close();
      }
      super.destroy();
   }

   protected void assertOnAllCaches(Object key, String value) {
      for (Cache c : caches()) {
         Object realVal = c.get(key);
         if (value == null) {
            assert realVal == null : "Expecting [" + key + "] to equal [" + value + "] on cache " + c.toString();
         } else {
            assert value.equals(realVal) : "Expecting [" + key + "] to equal [" + value + "] on cache " + c.toString();
         }
      }
      // Allow some time for all ClusteredGetCommands to finish executing
      TestingUtil.sleepThread(1000);
   }

   public class ExerciseAtomicSetTask implements Callable<Integer> {

      private int ncalls;
      private Set set;

      public ExerciseAtomicSetTask(Set set, int n) {
         ncalls = n;
         this.set = set;
      }

      @Override
      public Integer call() throws Exception {

         int ret = 0;
         for (int i = 0; i < ncalls; i++) {

            if (set.add(i)) {
               ret++;
            }

         }

         return ret;

      }
   }

}