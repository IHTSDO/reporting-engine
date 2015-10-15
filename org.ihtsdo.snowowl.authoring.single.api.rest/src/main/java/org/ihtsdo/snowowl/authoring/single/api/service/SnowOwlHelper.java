package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.commons.ClassUtils;
import com.b2international.commons.collections.Procedure;
import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.InternalComponentRef;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.events.Event;
import com.b2international.snowowl.core.events.util.AsyncSupport;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.ISnomedDescriptionService;
import com.b2international.snowowl.snomed.api.impl.FsnJoinerOperation;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.google.common.base.Optional;
import org.ihtsdo.snowowl.authoring.single.api.service.ts.SnomedServiceHelper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class SnowOwlHelper {

	@Autowired
	private ISnomedDescriptionService descriptionService;

	public Map<String, String> getFullySpecifiedNamesInOneHit(final Collection<String> conceptIds, String branch, final List<Locale> locales) {
		final Map<String, String> idFsnMap = new HashMap<>();
		if (!conceptIds.isEmpty()) {
			final SnomedTerminologyBrowser terminologyBrowser = ApplicationContext.getServiceForClass(SnomedTerminologyBrowser.class);
			final IComponentRef conceptRef = SnomedServiceHelper.createComponentRef(branch, conceptIds.iterator().next());
			InternalComponentRef internalComponentRef = ClassUtils.checkAndCast(conceptRef, InternalComponentRef.class);
			final IBranchPath iBranchPath = internalComponentRef.getBranch().branchPath();

			new FsnJoinerOperation<SnomedConceptIndexEntry>(conceptRef, locales, descriptionService) {
				@Override
				protected Collection<SnomedConceptIndexEntry> getConceptEntries(String id) {
					Set<SnomedConceptIndexEntry> indexEntries = new HashSet<>();
					for (String conceptId : conceptIds) {
						indexEntries.add(terminologyBrowser.getConcept(iBranchPath, conceptId));
					}
					return indexEntries;
				}

				@Override
				protected SnomedConceptIndexEntry convertConceptEntry(SnomedConceptIndexEntry snomedConceptIndexEntry, Optional<String> optional) {
					String conceptId = snomedConceptIndexEntry.getId();
					idFsnMap.put(conceptId, optional.or(conceptId));
					return snomedConceptIndexEntry;
				}
			}.run();
		}
		return idFsnMap;
	}

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
