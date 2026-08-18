package com.upivoicealert

import com.upivoicealert.data.datastore.UserProfileStore
import com.upivoicealert.data.repository.UserRepositoryImpl
import com.upivoicealert.domain.model.MerchantUser
import com.upivoicealert.utils.MerchantIdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryImplTest {

    private val merchantIdRegex = Regex("^SP-[0-9A-F]{6}$")

    @Test
    fun `new user profile creation generates a valid merchant id once`() = runTest {
        val store = FakeUserProfileStore()
        val repository = UserRepositoryImpl(store)

        repository.saveProfile("Rahul", "Rahul Store", "9876543210")

        val first = store.merchantId.first()
        assertTrue("merchant id must match SP-XXXXXX: $first", merchantIdRegex.matches(first))

        repository.saveProfile("Rahul Kumar", "Rahul Store 2", "9876500000")

        val second = store.merchantId.first()
        assertEquals("merchant id must be generated exactly once", first, second)
    }

    @Test
    fun `app restart keeps the same merchant id`() = runTest {
        val store = FakeUserProfileStore()
        val firstLaunch = UserRepositoryImpl(store)
        firstLaunch.saveProfile("Rahul", "Rahul Store", "9876543210")
        val idBeforeRestart = store.merchantId.first()
        assertTrue(merchantIdRegex.matches(idBeforeRestart))

        val afterRestart = UserRepositoryImpl(store)
        val user = afterRestart.getUser()

        assertEquals("restart must not regenerate the id", idBeforeRestart, user?.merchantId)
    }

    @Test
    fun `existing user keeps profile and gets an id automatically`() = runTest {
        val store = FakeUserProfileStore()
        store.seedProfile(
            name = "Meera",
            shopName = "Meera Store",
            phone = "9123456789",
            createdAt = 1_600_000_000_000L
        )
        val repository = UserRepositoryImpl(store)

        val user = repository.getUser()

        assertEquals("Meera", user?.name)
        assertEquals("Meera Store", user?.shopName)
        assertEquals("9123456789", user?.phoneNumber)
        assertEquals(1_600_000_000_000L, user?.createdAt)
        assertTrue("existing user must receive a merchant id", merchantIdRegex.matches(user?.merchantId.orEmpty()))
    }

    @Test
    fun `editing profile keeps the merchant id unchanged`() = runTest {
        val store = FakeUserProfileStore()
        val repository = UserRepositoryImpl(store)
        repository.saveProfile("Rahul", "Rahul Store", "9876543210")
        val originalId = store.merchantId.first()

        repository.saveProfile("Rahul Kumar", "Rahul Store 2", "9876500000")
        val edited = repository.getUser()

        assertEquals("editing must never change the merchant id", originalId, edited?.merchantId)
        assertEquals("Rahul Kumar", edited?.name)
        assertEquals("Rahul Store 2", edited?.shopName)
        assertEquals("9876500000", edited?.phoneNumber)
    }

    @Test
    fun `observing the user generates the id even before any profile save`() = runTest {
        val store = FakeUserProfileStore()
        val repository = UserRepositoryImpl(store)

        val observed = repository.observeUser().first()

        assertNull(observed)
        assertTrue(merchantIdRegex.matches(store.merchantId.first()))
    }

    @Test
    fun `generator produces only sp prefixed six hex char ids`() {
        repeat(100) {
            val id = MerchantIdGenerator.generate()
            assertTrue("unexpected id format: $id", merchantIdRegex.matches(id))
            assertTrue(id.startsWith("SP-"))
        }
    }

    @Test
    fun `two fresh installs generate distinct ids`() {
        val a = MerchantIdGenerator.generate()
        val b = MerchantIdGenerator.generate()
        assertNotEquals(a, b)
    }
}

private class FakeUserProfileStore : UserProfileStore {

    private val _userName = MutableStateFlow("")
    private val _shopName = MutableStateFlow("")
    private val _mobileNumber = MutableStateFlow("")
    private val _userCreatedAt = MutableStateFlow(0L)
    private val _merchantId = MutableStateFlow("")

    override val userName: Flow<String> get() = _userName
    override val shopName: Flow<String> get() = _shopName
    override val mobileNumber: Flow<String> get() = _mobileNumber
    override val userCreatedAt: Flow<Long> get() = _userCreatedAt
    override val merchantId: Flow<String> get() = _merchantId

    override suspend fun setMerchantId(id: String) { _merchantId.value = id }
    override suspend fun setUserCreatedAt(createdAt: Long) { _userCreatedAt.value = createdAt }
    override suspend fun setUserName(name: String) { _userName.value = name }
    override suspend fun setShopName(shopName: String) { _shopName.value = shopName }
    override suspend fun setMobileNumber(number: String) { _mobileNumber.value = number }

    fun seedProfile(name: String = "", shopName: String = "", phone: String = "", createdAt: Long = 0L) {
        _userName.value = name
        _shopName.value = shopName
        _mobileNumber.value = phone
        _userCreatedAt.value = createdAt
    }
}
