package org.infinispan.creson.container;

import org.infinispan.commons.api.BasicCache;
import org.infinispan.creson.object.Call;
import org.infinispan.creson.object.Reference;
import org.infinispan.creson.object.Utils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author Pierre Sutra
 */
public class FakeContainer extends AbstractContainer {
   
   private static ConcurrentMap<Reference,Object> objects = new ConcurrentHashMap<>();

   private BasicCache cache;
   private Reference reference;

   public FakeContainer(BasicCache cache, Class clazz, Object key,
         boolean readOptimization, boolean forceNew, Object... initArgs) {
      super(clazz, readOptimization, forceNew, initArgs);
      
      try {
         this.cache = cache;
         this.reference = new Reference(clazz,key);
         Object o = Utils.initObject(reference.getClazz(), initArgs);
         objects.putIfAbsent(reference,o);
         proxy = objects.get(reference);
      } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
         e.printStackTrace(); 
      } 
      
   }

   @Override
   public Reference getReference() {
      return reference;
   }

   @Override
   public synchronized void open() throws InterruptedException, ExecutionException, TimeoutException, IOException {
   }

   @Override
   public synchronized void close()
         throws InterruptedException, ExecutionException, TimeoutException, IOException {
   }

   @Override
   public void execute(Reference reference, Call call) {
      cache.put(reference,call);
   }

   @Override
   public BasicCache getCache() {
      return cache;
   }

}
