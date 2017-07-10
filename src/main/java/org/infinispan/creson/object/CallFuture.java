package org.infinispan.creson.object;

import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * @author Pierre Sutra
 */
public class CallFuture extends Call implements Future<Object> {

   private static Log log = LogFactory.getLog(CallFuture.class);

   private Object ret;
   private Object state;
   private int status; // 0 => init, 1 => done, -1 => cancelled

   @Deprecated
   public CallFuture(){}

   public CallFuture(UUID callID){
      super(callID);
      this.ret = null;
      this.status = 0;
   }

   public void set(Object r){

      synchronized (this) {

         if (status != 0) {
            return;
         }

         ret = r;
         status = 1;
         this.notifyAll();

      }

   }

   @Override
   public boolean cancel(boolean mayInterruptIfRunning) {
      synchronized (this) {
         if (status != 0)
            return false;
         status = -1;
         if (mayInterruptIfRunning)
            this.notifyAll();
      }
      return true;
   }

   @Override
   public Object get() throws InterruptedException, ExecutionException {
      synchronized (this) {
         if (status == 0)
            this.wait();
      }
      return (status == -1) ? null : ret;
   }

   @Override
   public Object get(long timeout, TimeUnit unit)
         throws InterruptedException, ExecutionException, TimeoutException {
      synchronized (this) {
         if (status == 0)
            this.wait(timeout);
      }
      if (status ==0) throw new TimeoutException(this + " failed");
      return (status == -1) ? null : ret;
   }

   public Object getState(){ 
      return state;
   }
   
   public void setState(Object state){ 
      this.state = state;
   }

   @Override
   public boolean isCancelled() {
      return status == -1;
   }

   @Override
   public boolean isDone() {
      return status == 1;
   }

   @Override
   public String toString() {
      return "Future["+getCallID()+","+ret+"]";
   }

   @Override
   public void writeExternal(ObjectOutput objectOutput) throws IOException {
      super.writeExternal(objectOutput);
      objectOutput.writeObject(ret);
      objectOutput.writeInt(status);
      objectOutput.writeObject(state);
   }

   @Override
   public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
      super.readExternal(objectInput);
      ret = objectInput.readObject();
      status = objectInput.readInt();
      state = objectInput.readObject();
   }
}
