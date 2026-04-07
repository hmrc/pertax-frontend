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

package models

final case class HomePageServices(services: Seq[HomePageService]) {

  private lazy val servicesByCategory: Map[HomePageServiceCategory, Seq[HomePageService]] = services.groupBy(_.category)

  lazy val myServices: Seq[MyService] = servicesByCategory
    .getOrElse(HomePageServiceCategory.MyServices, Seq.empty)
    .collect { case service: MyService => service }

  lazy val otherServices: Seq[OtherService] = servicesByCategory
    .getOrElse(HomePageServiceCategory.OtherServices, Seq.empty)
    .collect { case service: OtherService => service }
}
