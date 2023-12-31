package org.example

import com.hedera.hashgraph.sdk.*
import io.github.cdimascio.dotenv.Dotenv
import java.util.concurrent.TimeoutException

fun main() {
    val dotenv = Dotenv.load()
    val myAccountId = AccountId.fromString(dotenv["ACCOUNT_ID"]!!)
    val myPrivateKey = PrivateKey.fromString(dotenv["ACCOUNT_DER_PRIVATE_KEY"]!!)


    // Create your Hedera testnet client
    val client = Client.forTestnet()
    client.setOperator(myAccountId, myPrivateKey)

//    testAll(client)
    testChallenge(client)
}

fun testChallenge(client: Client) {
    val challengers = 15
    val treasuryNftInfo = makeNftsForChallenge(challengers, client, "Mega challenge", "MCH") ?: return
    for (i in 1 .. challengers) {
        val newAccount = createNewAccount(client) ?: continue
        transferNftToAccount(client, treasuryNftInfo.treasuryAccountInfo, newAccount, treasuryNftInfo.tokenId, i.toLong())
        println("-------------------------")
        println()
    }
}

fun testAll(client: Client) {
    // Treasury Key
    val treasuryKey = PrivateKey.generateED25519()
    val treasuryPublicKey = treasuryKey.publicKey


    // Create treasury account
    val treasuryAccount = AccountCreateTransaction()
        .setKey(treasuryPublicKey)
        .setInitialBalance(Hbar(10))
        .execute(client)

    val treasuryId = treasuryAccount.getReceipt(client).accountId


    // Alice Key
    val aliceKey = PrivateKey.generateED25519()
    val alicePublicKey = aliceKey.publicKey


    // Create Alice's account
    val aliceAccount = AccountCreateTransaction()
        .setKey(alicePublicKey)
        .setInitialBalance(Hbar(10))
        .execute(client)

    val aliceAccountId = aliceAccount.getReceipt(client).accountId


    // Supply Key
    val supplyKey = PrivateKey.generateED25519()
    val supplyPublicKey = supplyKey.publicKey


    // Create the NFT
    val nftCreate = TokenCreateTransaction()
        .setTokenName("diploma")
        .setTokenSymbol("GRAD")
        .setTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
        .setDecimals(0)
        .setInitialSupply(0)
        .setTreasuryAccountId(treasuryId)
        .setSupplyType(TokenSupplyType.FINITE)
        .setMaxSupply(250)
        .setSupplyKey(supplyKey)
        .freezeWith(client)


    // Sign the transaction with the treasury key
    val nftCreateTxSign = nftCreate.sign(treasuryKey)


    // Submit the transaction to a Hedera network
    val nftCreateSubmit = nftCreateTxSign.execute(client)


    // Get the transaction receipt
    val nftCreateRx = nftCreateSubmit.getReceipt(client)


    // Get the token ID
    val tokenId = nftCreateRx.tokenId


    // Log the token ID
    println("Created NFT with token ID $tokenId")


    // Max transaction fee as a constant
    val MAX_TRANSACTION_FEE = 20


    // IPFS content identifiers for which we will create a NFT
    val CID = arrayOf(
        "ipfs://bafyreiao6ajgsfji6qsgbqwdtjdu5gmul7tv2v3pd6kjgcw5o65b2ogst4/metadata.json",
        "ipfs://bafyreic463uarchq4mlufp7pvfkfut7zeqsqmn3b2x3jjxwcjqx6b5pk7q/metadata.json",
        "ipfs://bafyreihhja55q6h2rijscl3gra7a3ntiroyglz45z5wlyxdzs6kjh2dinu/metadata.json",
        "ipfs://bafyreidb23oehkttjbff3gdi4vz7mjijcxjyxadwg32pngod4huozcwphu/metadata.json",
        "ipfs://bafyreie7ftl6erd5etz5gscfwfiwjmht3b52cevdrf7hjwxx5ddns7zneu/metadata.json"
    )


    // Mint a new NFT
    var mintTx = TokenMintTransaction()
        .setTokenId(tokenId)
        .setMaxTransactionFee(Hbar(MAX_TRANSACTION_FEE.toLong()))


    //                .freezeWith(client);
    for (cid in CID) {
        mintTx.addMetadata(cid.toByteArray())
    }

    mintTx = mintTx.freezeWith(client)


    // Sign transaction with the supply key
    val mintTxSign = mintTx.sign(supplyKey)


    // Submit the transaction to a Hedera network
    val mintTxSubmit = mintTxSign.execute(client)


    // Get the transaction receipt
    val mintRx = mintTxSubmit.getReceipt(client)


    // Log the serial number
    println("Created NFT " + tokenId + " with serial: " + mintRx.serials)


    // Create the associate transaction and sign with Alice's key
    val associateAliceTx = TokenAssociateTransaction()
        .setAccountId(aliceAccountId)
        .setTokenIds(listOf(tokenId))
        .freezeWith(client)
        .sign(aliceKey)


    // Submit the transaction to a Hedera network
    val associateAliceTxSubmit = associateAliceTx.execute(client)


    // Get the transaction receipt
    val associateAliceRx = associateAliceTxSubmit.getReceipt(client)


    // Confirm the transaction was successful
    println("NFT association with Alice's account: " + associateAliceRx.status)


    // Check the balance before the NFT transfer for the treasury account
    val balanceCheckTreasury = AccountBalanceQuery().setAccountId(treasuryId)
        .execute(client)
    println("Treasury balance: " + balanceCheckTreasury.tokens + "NFTs of ID " + tokenId)


    // Check the balance before the NFT transfer for Alice's account
    val balanceCheckAlice = AccountBalanceQuery().setAccountId(aliceAccountId)
        .execute(client)
    println("Alice's balance: " + balanceCheckAlice.tokens + "NFTs of ID " + tokenId)


    // Transfer NFT from treasury to Alice
    // Sign with the treasury key to authorize the transfer
    val tokenTransferTx = TransferTransaction()
        .addNftTransfer(NftId(tokenId, 1), treasuryId, aliceAccountId)
        .freezeWith(client)
        .sign(treasuryKey)

    val tokenTransferSubmit = tokenTransferTx.execute(client)
    val tokenTransferRx = tokenTransferSubmit.getReceipt(client)

    println("NFT transfer from Treasury to Alice: " + tokenTransferRx.status)


    // Check the balance for the treasury account after the transfer
    val balanceCheckTreasury2 = AccountBalanceQuery().setAccountId(treasuryId)
        .execute(client)
    println("Treasury balance: " + balanceCheckTreasury2.tokens + "NFTs of ID " + tokenId)


    // Check the balance for Alice's account after the transfer
    val balanceCheckAlice2 = AccountBalanceQuery().setAccountId(aliceAccountId)
        .execute(client)
    println("Alice's balance: " + balanceCheckAlice2.tokens + "NFTs of ID " + tokenId)
}

data class AccountInfo(val accountId: AccountId, val key: PrivateKey)

@Throws(ReceiptStatusException::class, TimeoutException::class, PrecheckStatusException::class)
fun createNewAccount(client: Client, initialBalance: Long = 10): AccountInfo? {
    val accountPrivateKey = PrivateKey.generateED25519()
    val account = AccountCreateTransaction()
        .setKey(accountPrivateKey.publicKey)
        .setInitialBalance(Hbar(initialBalance))
        .execute(client)

    val accountId = account.getReceipt(client).accountId
    println("New account created with ID $accountId")

    if (accountId == null) return null
    return AccountInfo(accountId, accountPrivateKey)
}

fun getAccountInfo(accountId: String, privateKey: String): AccountInfo {
    return AccountInfo(
        AccountId.fromString("0.0.1234"),
        PrivateKey.fromString("302e020100300506032b6570042204208f5019c14c45d91786c6f9a4a1ae2b3b592239eac006deb8a4b8f15a50ca874c")
    )
}

/**
 * Retrieves the account balance of the specified account ID.
 *
 * @param accountId the ID of the account
 * @return the account balance in Hbars
 */
fun getAccountBalance(client: Client, accountId: AccountId): Hbar {
    return AccountBalanceQuery()
        .setAccountId(accountId)
        .execute(client).hbars
}

/**
 * Transfers a specified amount of HBAR from one account to another.
 *
 * @param senderId the ID of the account from which the HBAR is to be transferred
 * @param receiverId the ID of the account to which the HBAR is to be transferred
 * @param amount the amount of HBAR to be transferred
 * @return the response of the transaction
 */
fun transferHBAR(client: Client, senderId: AccountId, receiverId: AccountId, amount: Hbar): TransactionResponse {
    return TransferTransaction()
        .addHbarTransfer(senderId, amount.negated())
        .addHbarTransfer(receiverId, amount)
        .execute(client)
}

fun topUpHBARs(client: Client, accountId: AccountId, amount: Hbar): TransactionResponse {
    return TransferTransaction().addHbarTransfer(accountId, amount).execute(client)
}

fun withdrawHBARs(client: Client, accountId: AccountId, amount: Hbar): TransactionResponse {
    return TransferTransaction().addHbarTransfer(accountId, amount.negated()).execute(client)
}

data class NftTreasuryInfo(val tokenId: TokenId, val treasuryAccountInfo: AccountInfo)

fun makeNftsForChallenge(
    numberOfChallengers: Int,
    client: Client,
    challengeName: String,
    challengeSymbol: String
): NftTreasuryInfo {
    val accountInfo: AccountInfo? = createNewAccount(client)
    if (accountInfo == null) {
        throw RuntimeException("Failed to create new account")
    }
    val supplyKey = PrivateKey.generateED25519()
    val supplyPublicKey = supplyKey.publicKey

    val treasuryId = accountInfo.accountId
    val nftCreate = TokenCreateTransaction()
        .setTokenName("Challenge $challengeName NFT")
        .setTokenSymbol(challengeSymbol)
        .setTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
        .setDecimals(0)
        .setInitialSupply(0)
        .setTreasuryAccountId(treasuryId)
        .setSupplyType(TokenSupplyType.FINITE)
        .setMaxSupply(250)
        .setSupplyKey(supplyKey)
        .freezeWith(client)

    // Sign the transaction with the treasury key
    val nftCreateTxSign = nftCreate.sign(accountInfo.key)
    // Submit the transaction to a Hedera network
    val nftCreateSubmit = nftCreateTxSign.execute(client)
    // Get the transaction receipt
    val nftCreateRx = nftCreateSubmit.getReceipt(client)
    // Get the token ID
    val tokenId = nftCreateRx.tokenId ?: throw RuntimeException("Failed to create token")
    // Log the token ID
    println("Created NFT with token ID $tokenId")

    fun mintBatchOfNfts(curCounterValue: Int): Int {
        val MAX_RETRIES = 5
        var retries = 0
        while (retries < MAX_RETRIES) {
            try {
                var batchNftsCounter = curCounterValue

                val MAX_TRANSACTION_FEE = 20
                var mintTx = TokenMintTransaction()
                    .setTokenId(tokenId)
                    .setMaxTransactionFee(Hbar(MAX_TRANSACTION_FEE.toLong()))

                for (i in 0 until 10) {
                    batchNftsCounter++
                    mintTx.addMetadata("$challengeName $batchNftsCounter".toByteArray())
                    if (batchNftsCounter >= numberOfChallengers) {
                        break
                    }
                }

                mintTx = mintTx.freezeWith(client)

                val mintTxSign = mintTx.sign(supplyKey)
                val mintTxSubmit = mintTxSign.execute(client)
                val mintRx = mintTxSubmit.getReceipt(client)
                println("Created NFT " + tokenId + " with serial: " + mintRx.serials)
                return batchNftsCounter
            } catch (ex: PrecheckStatusException) {
                if (ex.status == Status.BUSY) {
                    retries++;
                    println("Retry attempt: " + retries);
                } else {
                    throw ex;
                }
            }
        }
        return curCounterValue
    }

    var nftsCounter = 0
    while (nftsCounter < numberOfChallengers) {
        nftsCounter = mintBatchOfNfts(nftsCounter)
    }

    return NftTreasuryInfo(tokenId, accountInfo)
}

fun transferNftToAccount(
    client: Client,
    treasuryAccountInfo: AccountInfo,
    receiverAccountInfo: AccountInfo,
    tokenId: TokenId,
    serial: Long
) {
    val receiverAccountId: AccountId = receiverAccountInfo.accountId
    val receiverKey: PrivateKey = receiverAccountInfo.key

    val treasuryId = treasuryAccountInfo.accountId
    val treasuryKey = treasuryAccountInfo.key

    // Create the associate transaction and sign with Alice's key
    val associateAliceTx = TokenAssociateTransaction()
        .setAccountId(receiverAccountId)
        .setTokenIds(listOf(tokenId))
        .freezeWith(client)
        .sign(receiverKey)


    // Submit the transaction to a Hedera network
    val associateAliceTxSubmit = associateAliceTx.execute(client)


    // Get the transaction receipt
    val associateAliceRx = associateAliceTxSubmit.getReceipt(client)


    // Confirm the transaction was successful
    println("NFT association with Alice's account: " + associateAliceRx.status)


    // Check the balance before the NFT transfer for the treasury account
    val balanceCheckTreasury = AccountBalanceQuery().setAccountId(treasuryId)
        .execute(client)
    println("Treasury balance: " + balanceCheckTreasury.tokens + "NFTs of ID " + tokenId)


    // Check the balance before the NFT transfer for Alice's account
    val balanceCheckAlice = AccountBalanceQuery().setAccountId(receiverAccountId)
        .execute(client)
    println("Alice's balance: " + balanceCheckAlice.tokens + "NFTs of ID " + tokenId)


    // Transfer NFT from treasury to Alice
    // Sign with the treasury key to authorize the transfer
    val tokenTransferTx = TransferTransaction()
        .addNftTransfer(NftId(tokenId, serial), treasuryId, receiverAccountId)
        .freezeWith(client)
        .sign(treasuryKey)

    val tokenTransferSubmit = tokenTransferTx.execute(client)
    val tokenTransferRx = tokenTransferSubmit.getReceipt(client)

    println("NFT transfer from Treasury to Alice: " + tokenTransferRx.status)


    // Check the balance for the treasury account after the transfer
    val balanceCheckTreasury2 = AccountBalanceQuery().setAccountId(treasuryId)
        .execute(client)
    println("Treasury balance: " + balanceCheckTreasury2.tokens + "NFTs of ID " + tokenId)


    // Check the balance for Alice's account after the transfer
    val balanceCheckAlice2 = AccountBalanceQuery().setAccountId(receiverAccountId)
        .execute(client)
    println("Alice's balance: " + balanceCheckAlice2.tokens + "NFTs of ID " + tokenId)
}

