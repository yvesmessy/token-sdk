package com.r3.corda.sdk.token.workflow.utilities

import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.schemas.DistributionRecord
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * Utility function to persist a new entity pertaining to a distribution record.
 * TODO: Add some error handling.
 */
fun addPartyToDistributionList(services: ServiceHub, party: Party, linearId: UniqueIdentifier) {
    // Create an persist a new entity.
    val distributionRecord = DistributionRecord(linearId.id, party)
    services.withEntityManager { persist(distributionRecord) }
}

val LedgerTransaction.participants: List<AbstractParty>
    get() {
        val inputParticipants = inputStates.flatMap(ContractState::participants)
        val outputParticipants = outputStates.flatMap(ContractState::participants)
        return inputParticipants + outputParticipants
    }

fun LedgerTransaction.ourSigningKeys(services: ServiceHub): List<PublicKey> {
    val signingKeys = commands.flatMap(CommandWithParties<*>::signers)
    return services.keyManagementService.filterMyKeys(signingKeys).toList()
}

fun AbstractParty.toParty(services: ServiceHub) = services.identityService.requireWellKnownPartyFromAnonymous(this)

fun List<AbstractParty>.toWellKnownParties(services: ServiceHub): List<Party> {
    return map(services.identityService::requireWellKnownPartyFromAnonymous)
}

// Needs to deal with confidential identities.
fun requireSessionsForParticipants(participants: List<Party>, sessions: Set<FlowSession>) {
    val sessionParties = sessions.map(FlowSession::counterparty)
    require(sessionParties.containsAll(participants)) {
        val missing = participants - sessionParties
        "There should be a flow session for all state participants. Sessions are missing for $missing."
    }
}

fun FlowLogic<*>.sessionsForParicipants(states: List<ContractState>, otherParties: Iterable<Party>): Set<FlowSession> {
    val stateParties = states.flatMap(ContractState::participants)
    val wellKnownStateParties = stateParties.toWellKnownParties(serviceHub)
    val allParties = wellKnownStateParties + otherParties
    return allParties.map(::initiateFlow).toSet()
}

fun <T : TokenType> FlowLogic<*>.updateDistributionList(tokens: List<AbstractToken<T>>) {
    tokens.forEach { token ->
        val tokenType = token.tokenType
        if (tokenType is TokenPointer<*>) {
            addPartyToDistributionList(serviceHub, token.holder.toParty(serviceHub), tokenType.pointer.pointer)
        }
    }
}

