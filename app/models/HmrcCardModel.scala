package models

import play.twirl.api.Html
import uk.gov.hmrc.govukfrontend.views.html.components.GovukTag as Tag

enum Type:
  case BasicCard
  case BasicCardWithDueDate
  case SectionCard
  case NoLinkCard
  case NewTabCard

class Heading(val text: String, val url: Option[String])
class Body(val content: Html)
class Hint(val content: Option[String], val tag: Option[Tag])

case class HmrcCardModel(
  cardType: Type,
  heading: Heading,
  body: Option[Body],
  hint: Option[Hint],
)