package uk.gov.justice.digital.hmpps.indexer.health.wiremock

import com.github.tomakehurst.wiremock.WireMockServer

class CommunityMockServer : WireMockServer(8096)

class OAuthMockServer : WireMockServer(8090)