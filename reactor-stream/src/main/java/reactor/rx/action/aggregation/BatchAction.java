/*
 * Copyright (c) 2011-2014 Pivotal Software, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package reactor.rx.action.aggregation;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.subscriber.SerializedSubscriber;
import reactor.core.support.Bounded;
import reactor.fn.Consumer;
import reactor.fn.Pausable;
import reactor.fn.timer.Timer;
import reactor.rx.action.Action;
import reactor.rx.subscription.BatchSubscription;
import reactor.rx.subscription.PushSubscription;

import java.util.concurrent.TimeUnit;

/**
 * @author Stephane Maldini
 * @since 1.1
 */
public abstract class BatchAction<T, V> extends Action<T, V> {

	protected final boolean  next;
	protected final boolean  flush;
	protected final boolean  first;
	protected final int      batchSize;
	protected final long     timespan;
	protected final TimeUnit unit;
	protected final Timer    timer;
	protected final Consumer<Long> flushTask;

	private volatile int index = 0;
	private Pausable timespanRegistration;

	public BatchAction(
	  int batchSize, boolean next, boolean first, boolean flush) {
		this(batchSize, next, first, flush, -1l, null, null);
	}

	public BatchAction(int batchSize, boolean next, boolean first, boolean flush,
	                   long timespan, TimeUnit unit, final Timer timer) {
		super(batchSize);
		if (timespan > 0) {
			this.unit = unit != null ? unit : TimeUnit.SECONDS;
			this.timespan = timespan;
			this.timer = timer;
			this.flushTask = new Consumer<Long>() {
				@Override
				public void accept(Long aLong) {
					if (isPublishing()) {
						synchronized (timer) {
							if(index == 0) {
								return;
							} else {
								index = 0;
							}
						}
						flushCallback(null);
					}
				}
			};
		} else {
			this.timespan = -1L;
			this.timer = null;
			this.flushTask = null;
			this.unit = null;
		}
		this.first = first;
		this.flush = flush;
		this.next = next;
		this.batchSize = batchSize;
		//this.capacity = batchSize;
	}

	@Override
	protected PushSubscription<T> createTrackingSubscription(Subscription subscription) {
		return new BatchSubscription<T>(subscription, this, batchSize);
	}

	@Override
	public void subscribe(Subscriber<? super V> subscriber) {
		if (timer != null) {
			super.subscribe(SerializedSubscriber.create(subscriber));
		} else {
			super.subscribe(subscriber);
		}
	}

	@Override
	public boolean isExposedToOverflow(Bounded upstream) {
		return false;
	}

	protected void nextCallback(T event) {
	}

	protected void flushCallback(T event) {
	}

	protected void firstCallback(T event) {
	}

	@Override
	protected void doNext(final T value) {
		final int index;
		if ( timer != null ){
			synchronized (timer) {
				index = ++this.index;
			}
		} else {
			index = ++this.index;
		}

		if (index == 1) {
			if (timer != null) {
				timespanRegistration = timer.submit(flushTask, timespan, unit);
			}
			if (first) {
				firstCallback(value);
			}
		}

		if (next) {
			nextCallback(value);
		}

		if (index % batchSize == 0) {
			if(timer != null && timespanRegistration != null) {
				timespanRegistration.cancel();
				timespanRegistration = null;
			}
			if ( timer != null) {
				synchronized (timer) {
					this.index = 0;
				}
			}else {
				this.index = 0;
			}
			if (flush) {
				flushCallback(value);
			}
		}
	}

	@Override
	protected void doComplete() {
		flushCallback(null);
		super.doComplete();
	}


	@Override
	public String toString() {
		return super.toString() + "{" + (timer != null ? "timed - " + timespan + " " + unit : "") + " batchSize=" +
		  index + "/" +
		  batchSize + " [" + (int) ((((float) index) / ((float) batchSize)) * 100) + "%]";
	}

}
