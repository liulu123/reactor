/*
 * Copyright (c) 2011-2015 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.reactivestreams.tck;

import org.junit.Test;
import reactor.Processors;
import reactor.core.processor.ProcessorGroup;
import reactor.fn.tuple.Tuple1;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.action.CompositeAction;
import reactor.rx.broadcast.Broadcaster;

import java.util.concurrent.TimeUnit;

/**
 * @author Stephane Maldini
 */
@org.testng.annotations.Test
public class StreamAndProcessorGroupTests extends AbstractStreamVerification {

	@Override
	public CompositeAction<Integer, Integer> createProcessor(int bufferSize) {

		Stream<String> otherStream = Streams.just("test", "test2", "test3");
		System.out.println("Providing new processor");

		ProcessorGroup<Integer> asyncGroup =
		  Processors.asyncGroup("stream-tck", bufferSize, 4, Throwable::printStackTrace);

		return Broadcaster.<Integer>
		  passthrough()
		  .dispatchOn(asyncGroup)
		  .partition(2)
		  .flatMap(stream -> stream
			  .dispatchOn(asyncGroup)
			  .observe(this::monitorThreadUse)
			  .scan((prev, next) -> next)
			  .map(integer -> -integer)
			  .filter(integer -> integer <= 0)
			  .sample(1)
			  .map(integer -> -integer)
			  .buffer(batch, 50, TimeUnit.MILLISECONDS)
			  .<Integer>split()
			  .flatMap(i -> Streams.zip(Streams.just(i), otherStream, Tuple1::getT1))
		  )
		  .dispatchOn(asyncGroup)
		  //.log("end")
		  .when(Throwable.class, Throwable::printStackTrace)
		  .combine();
	}

	@Override
	@Test
	public void testHotIdentityProcessor() throws InterruptedException {
			super.testHotIdentityProcessor();
	}

	@Override
	@Test
	public void testColdIdentityProcessor() throws InterruptedException {
		super.testColdIdentityProcessor();
	}
}
