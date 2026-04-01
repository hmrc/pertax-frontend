package routePages

import controllers.bindable.AddrType
import play.api.libs.json.JsPath

case class DefaultedStartDatePage(typ: AddrType) extends QuestionPage[Boolean] {

  override def toString: String = "defaultedStartDate"

  override def path: JsPath = JsPath \ s"$typ" \ toString
}
