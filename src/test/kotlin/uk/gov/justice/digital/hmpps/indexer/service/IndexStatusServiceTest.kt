package uk.gov.justice.digital.hmpps.indexer.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.client.IndicesClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.helpers.indexStatus
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import java.util.*

class IndexStatusServiceTest {

  private val indexStatusRepository = mock<IndexStatusRepository>()
  private val elasticSearchClient = mock<RestHighLevelClient>()
  private val indexClient = mock<IndicesClient>()
  private val indexStatusService = IndexStatusService(indexStatusRepository, elasticSearchClient)

  @Nested
  inner class InitialiseIndexWhenRequired {
    @Nested
    inner class NoIndex {
      @BeforeEach
      internal fun setUp() {

        whenever(elasticSearchClient.indices()).thenReturn(indexClient)
        whenever(indexClient.exists(any<GetIndexRequest>(), any())).thenReturn(false)
      }

      @Test
      internal fun `will create a new index`() {
        indexStatusService.initialiseIndexWhenRequired()

        verify(indexClient).create(check<CreateIndexRequest> {
          assertThat(it.index()).isEqualTo("offender-index-status")
        }, any())
      }

      @Test
      internal fun `will add initial status to index`() {
        val expectedNewIndexStatus = IndexStatus(otherIndexState = IndexState.NEW, currentIndex = SyncIndex.NONE, currentIndexState = IndexState.NEW)

        indexStatusService.initialiseIndexWhenRequired()
        verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
          assertThat(savedIndexStatus).isEqualTo(expectedNewIndexStatus)
        })

      }
    }
    @Nested
    inner class IndexAlreadyExists {
      @BeforeEach
      internal fun setUp() {

        whenever(elasticSearchClient.indices()).thenReturn(indexClient)
        whenever(indexClient.exists(any<GetIndexRequest>(), any())).thenReturn(true)
      }

      @Test
      internal fun `will not create a new index`() {
        indexStatusService.initialiseIndexWhenRequired()

        verify(indexClient, never()).create(any<CreateIndexRequest>(), any())
      }

      @Test
      internal fun `will not add initial status to index`() {
        indexStatusService.initialiseIndexWhenRequired()
        verify(indexStatusRepository, never()).save<IndexStatus>(any())
      }
    }
  }

  @Nested
  inner class GetCurrentIndexStatus {

    @Test
    fun `An existing index status should be returned`() {
      val existingIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexStatus))

      val actualIndexStatus = indexStatusService.getIndexStatus()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      assertThat(actualIndexStatus).isEqualTo(existingIndexStatus)
    }

  }

  @Nested
  inner class MarkBuildInProgress {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusRepository.save(any<IndexStatus>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `Already building index does nothing`() {
      val existingIndexInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Not currently building index saves status building`() {
      val existingIndexNotInProgress = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.NEW)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      val newIndexStatus = indexStatusService.markBuildInProgress()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.otherIndex).isEqualTo(SyncIndex.BLUE)
        assertThat(savedIndexStatus.otherIndexStartBuildTime).isNotNull()
        assertThat(savedIndexStatus.otherIndexState).isEqualTo(IndexState.BUILDING)
      })

      assertThat(newIndexStatus).isNotNull
    }
  }

  @Nested
  inner class MarkBuildComplete {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusRepository.save(any<IndexStatus>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `Not currently building index does nothing`() {
      val existingIndexNotInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Currently building index updates repository to completed`() {
      val existingIndexInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      val newIndexStatus = indexStatusService.markBuildCompleteAndSwitchIndex()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.GREEN)
        assertThat(savedIndexStatus.currentIndex).isNotNull()
        assertThat(savedIndexStatus.currentIndexState).isEqualTo(IndexState.COMPLETED)
      })

      assertThat(newIndexStatus).isNotNull
    }
  }

  @Nested
  inner class MarkBuildCancelled {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusRepository.save(any<IndexStatus>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `Build not currently in progress does nothing`() {
      val existingIndexNotInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Build currently in progress updates repository to cancelled`() {
      val existingIndexInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      val newIndexStatus = indexStatusService.markBuildCancelled()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.otherIndex).isEqualTo(SyncIndex.GREEN)
        assertThat(savedIndexStatus.otherIndexEndBuildTime).isNotNull()
        assertThat(savedIndexStatus.otherIndexState).isEqualTo(IndexState.CANCELLED)
      })

      assertThat(newIndexStatus).isNotNull
    }
  }
}