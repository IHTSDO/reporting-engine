package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.commons.collections.Procedure;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.events.Event;
import com.b2international.snowowl.core.events.util.AsyncSupport;
import com.b2international.snowowl.eventbus.IEventBus;

import java.util.concurrent.CountDownLatch;

public class SnowOwlBusHelper {

	<E extends Event, R> R makeBusRequest(E event, Class<R> replyClass, String errorMessage, BranchService branchService) throws ServiceException {
		final AsyncResult<R> asyncResult = new AsyncResult<>();
		new AsyncSupport<>(getSnowOwlBackendEventBus(), replyClass)
				.send(event)
				.then(new Procedure<R>() { @Override protected void doApply(R reply) {
					asyncResult.setResult(reply);
				}})
				.fail(new Procedure<Throwable>() { @Override protected void doApply(Throwable t) {
					asyncResult.setThrowable(t);
				}});

		asyncResult.await();
		if (!asyncResult.isSuccess()) {
			throw new ServiceException(errorMessage, asyncResult.getThrowable());
		} else {
			return asyncResult.getResult();
		}
	}

	private IEventBus getSnowOwlBackendEventBus() {
		return ApplicationContext.getInstance().getService(IEventBus.class);
	}

	private static class AsyncResult<T> {

		private final CountDownLatch latch;
		private T result;
		private Throwable throwable;
		private boolean success;

		public AsyncResult() {
			latch = new CountDownLatch(1);
		}

		public T getResult() {
			return result;
		}

		public void setResult(T result) {
			this.result = result;
			success = true;
			latch.countDown();
		}

		public Throwable getThrowable() {
			return throwable;
		}

		public void setThrowable(Throwable throwable) {
			this.throwable = throwable;
			latch.countDown();
		}

		public boolean isSuccess() {
			return success;
		}

		public void await() {
			try {
				latch.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
