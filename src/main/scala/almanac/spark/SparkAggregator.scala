package almanac.spark

import almanac.model.Metric._
import almanac.model.TimeSpan.EVER
import almanac.model._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Duration, Minutes}

import scala.language.postfixOps

/**
 * Time span levels and Geo precision levels to be aggregated
 *
 * please reference to `SparkMetricsAggregator.aggregate` for more detail
 *
 * @param geoPrecisions
 * @param timeSpans
 */
case class AggregationSchedules(geoPrecisions: List[Int], timeSpans: List[TimeSpan])

trait AlmanacMetrcRDDRepositoryFactory {
  def apply(schedules: AggregationSchedules)(implicit sc: SparkContext): MetricRDDRepository
}

object SparkMetricsAggregator {

  implicit class RDDMetricsExtension(val source: RDD[Metric]) extends MetricsAggregator[RDD[Metric]]  {
    override def aggregate(func: Key => Key) =
      source map (m => func(m.key) -> m.value) reduceByKey (_+_) map (t => Metric(t._1, t._2))
  }

  implicit class DStreamMetricsExtension(val source: DStream[Metric]) extends MetricsAggregator[DStream[Metric]] {
    override def aggregate(func: Key => Key) =
      source map (m => func(m.key) -> m.value) reduceByKey (_+_) map (t => Metric(t._1, t._2))

    def stats(interval: Duration) = source window(interval, interval) count() print()

    /**
     * aggregate geo and save result stream
     *
     * @param stream
     * @param precision
     * @param span
     * @return
     */
    private def geoProcess(stream: DStream[Metric], precision: Int, span: TimeSpan)
                          (implicit repo: MetricRDDRepository)= {
      val resultStream = stream aggregateByGeoPrecision precision
      repo.save(precision, span, resultStream)
      resultStream
    }

    /**
     * aggregate time and handle result stream
     *
     * @param stream
     * @param precision
     * @param span
     * @return
     */
    private def timeProcess(stream: DStream[Metric], precision: Int, span: TimeSpan)
                           (implicit repo: MetricRDDRepository)= {
      val resultStream = stream aggregateByTimeSpan span
      repo.save(precision, span, resultStream)
      resultStream
    }

    private def keyProcess(stream: DStream[Metric], precision: Int, span: TimeSpan)
                              (implicit repo: MetricRDDRepository) =
      // TODO: configuration of window span
      repo.saveKeys(stream window(Minutes(1), Minutes(1)) aggregateByTimeSpan EVER map (_.key))

    /**
     * aggregate the first timeSchedule to the intial stream
     * then aggregate on each level of timeSchedules and geoSchedules like below:
     *
     * Seq(HOUR, DAY, EVER) Seq(8, 4, GLOBAL)
     *
     * in this case HOUR is the intial time span level for aggregation
     *
     * 12, RAW -> initial stream -> 8, HOUR -> DAY -> EVER
     *                                 |
     *                                 V
     *                              4, HOUR -> DAY -> EVER
     *                                 |
     *                                 V
     *                         GLOBAL, HOUR -> DAY -> EVER
     *
     * the return value is the last aggregated stream in the above case: GLOBAL / EVER
     * @param repo the stream to be aggregated
     * @param schedules
     * @return the stream of the last aggregated stream
     */
    def aggregateWithSchedule(schedules: AggregationSchedules = defaultSchedules)(implicit repo: MetricRDDRepository) = {
      // aggregate first level of time span
      val intialTimeSpan :: otherTimeSchedules = schedules.timeSpans.sorted
      val initialStream = source aggregateByTimeSpan intialTimeSpan
      // aggregate
      (initialStream /: schedules.geoPrecisions.sorted.reverse) ((tranStream, precision) => {
        // aggregate geo and save result stream
        (geoProcess(tranStream, precision, intialTimeSpan) /: otherTimeSchedules) ( (geoResult, span) => {
          // aggregate fact and handle result stream
          keyProcess(geoResult, precision, span)
          // aggregate time and handle result stream
          timeProcess(geoResult, precision, span)
        })
      })
    }
  }

  val defaultSchedules = AggregationSchedules(List(GeoHash.GLOBAL), List(TimeSpan.EVER))
}