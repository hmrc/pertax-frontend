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

//todo: change link to an Option so we can disable an access
//todo: change hintMessage to an Option as well
final case class MyService(
  title: String,
  link: String,
  hintText: String,
  divAttr: Map[String, String] = Map.empty,
  gaAction: Option[String] = None,
  gaLabel: Option[String] = None,
  id: Option[String] = None
)
