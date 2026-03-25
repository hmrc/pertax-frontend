/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package job

import com.google.inject.{Inject, Injector, Singleton}
import play.api.inject.ApplicationLifecycle
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.quartz.spi.{JobFactory, TriggerFiredBundle}

import java.util.Date
import scala.concurrent.Future
import scala.util.Random

@Singleton
class GuiceJobFactory @Inject() (injector: Injector) extends JobFactory {
  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job =
    injector.getInstance(bundle.getJobDetail.getJobClass)
}

@Singleton
class JobScheduler @Inject() (lifecycle: ApplicationLifecycle, guiceJobFactory: GuiceJobFactory) {

  val scheduler = StdSchedulerFactory.getDefaultScheduler
  scheduler.setJobFactory(guiceJobFactory)

  val job = JobBuilder
    .newJob(classOf[FixAddressJob])
    .withIdentity("myJob", "group1")
    .build()

  val jitterMillis = Random.nextInt(5_000) // up to 10 seconds
  val startTime    = new Date(System.currentTimeMillis() + jitterMillis)

  val trigger = TriggerBuilder
    .newTrigger()
    .withIdentity("myTrigger", "group1")
    .startAt(startTime)
    .withSchedule(
      SimpleScheduleBuilder
        .simpleSchedule()
        .withIntervalInSeconds(6)
        .repeatForever()
    )
    .build()

  scheduler.start()
  scheduler.scheduleJob(job, trigger)

  // Shut down cleanly when Play stops
  lifecycle.addStopHook(() => Future.successful(scheduler.shutdown()))
}
