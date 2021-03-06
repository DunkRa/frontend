package model

import conf.Switches.DoubleCacheTimesSwitch
import org.joda.time.DateTime
import org.scala_tools.time.Imports._
import play.api.mvc.Result
import scala.concurrent.duration.Duration

object Cached extends implicits.Dates {

  private val cacheableStatusCodes = Seq(200, 404)

  private val tenDaysInSeconds = 864000

  def apply(seconds: Int)(result: Result): Result = {
    if (cacheableStatusCodes.exists(_ == result.header.status)) cacheHeaders(seconds, result) else result
  }

  def apply(duration: Duration)(result: Result): Result = {
    apply(duration.toSeconds.toInt)(result)
  }

  def apply(metaData: MetaData)(result: Result): Result = {
    if (cacheableStatusCodes.exists(_ == result.header.status)) cacheHeaders(metaData.cacheSeconds, result) else result
  }

  private def cacheHeaders(seconds: Int, result: Result) = {
    val now = DateTime.now
    val expiresTime = now + seconds.seconds
    val maxAge = if (DoubleCacheTimesSwitch.isSwitchedOn) seconds * 2 else seconds

    // NOTE, if you change these headers make sure they are compatible with our Edge Cache

    // see
    // http://tools.ietf.org/html/rfc5861
    // http://www.fastly.com/blog/stale-while-revalidate
    // http://docs.fastly.com/guides/22966608/40347813
    val staleWhileRevalidateSeconds = math.max(maxAge / 10, 1)
    val cacheControl = s"max-age=$maxAge, stale-while-revalidate=$staleWhileRevalidateSeconds, stale-if-error=$tenDaysInSeconds"
    result.withHeaders(
      "Surrogate-Control" -> cacheControl,
      "Cache-Control" -> cacheControl,
      "Expires" -> expiresTime.toHttpDateTimeString,
      "Date" -> now.toHttpDateTimeString
    )
  }
}

object NoCache {
  def apply(result: Result): Result = result.withHeaders("Cache-Control" -> "no-cache", "Pragma" -> "no-cache")
}
