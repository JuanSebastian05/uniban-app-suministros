package co.edu.udea.uniban.suministros

import androidx.lifecycle.SavedStateHandle
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.Registration
import co.edu.udea.uniban.suministros.data.auth.SessionState
import co.edu.udea.uniban.suministros.ui.auth.RegisterError
import co.edu.udea.uniban.suministros.ui.auth.RegisterViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val fake = FakeAuthRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeAuthRepository = fake,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = RegisterViewModel(repository, saved)

    private fun RegisterViewModel.fill(
        name: String = "Ana Pérez",
        document: String = "1020304050",
        phone: String = "",
        email: String = "ana@unibanagro.com",
        password: String = "secreto1",
        confirm: String = password,
    ) {
        changeName(name)
        changeDocument(document)
        changePhone(phone)
        changeEmail(email)
        changePassword(password)
        changeConfirmPassword(confirm)
    }

    private fun RegisterViewModel.submitted(): RegisterError? {
        submit()
        return state.error
    }

    @Test
    fun requiredFieldsAreValidatedWithoutCallingRepository() {
        assertEquals(RegisterError.EmptyFields, viewModel().submitted())
        assertEquals(RegisterError.EmptyFields, viewModel().apply { fill(name = "  ") }.submitted())
        assertEquals(RegisterError.EmptyFields, viewModel().apply { fill(document = "") }.submitted())
        assertEquals(RegisterError.EmptyFields, viewModel().apply { fill(confirm = "") }.submitted())
        assertTrue(fake.registerCalls.isEmpty())
    }

    @Test
    fun emailFormatPasswordLengthAndConfirmation() {
        assertEquals(RegisterError.InvalidEmail, viewModel().apply { fill(email = "ana@") }.submitted())
        assertEquals(RegisterError.ShortPassword, viewModel().apply { fill(password = "12345") }.submitted())
        assertEquals(
            RegisterError.PasswordMismatch,
            viewModel().apply { fill(password = "secreto1", confirm = "secreto2") }.submitted(),
        )
        assertTrue(fake.registerCalls.isEmpty())
    }

    @Test
    fun successSendsTrimmedDataAndOpensSession() {
        val vm = viewModel()
        vm.fill(name = " Ana Pérez ", document = " 1020304050 ", phone = "  ", email = " ana@unibanagro.com ")
        vm.submit()
        assertEquals(
            listOf(Registration("Ana Pérez", "1020304050", null, "ana@unibanagro.com") to "secreto1"),
            fake.registerCalls,
        )
        assertEquals(SessionState.Authenticated("uid-new", "ana@unibanagro.com"), fake.sessionState.value)
        assertNull(vm.state.error)
        assertTrue(vm.state.isLoading)
    }

    @Test
    fun phoneIsSentWhenPresent() {
        viewModel().apply { fill(phone = "3001234567") }.submit()
        assertEquals("3001234567", fake.registerCalls.single().first.phone)
    }

    @Test
    fun everyAuthErrorIsExposedAndStopsLoading() {
        AuthError.entries.forEach { error ->
            val repo = FakeAuthRepository().apply { onRegister = { _, _ -> Result.failure(AuthException(error)) } }
            val vm = viewModel(repo)
            vm.fill()
            vm.submit()
            assertEquals(RegisterError.Auth(error), vm.state.error)
            assertFalse(vm.state.isLoading)
            assertEquals(SessionState.Unauthenticated, repo.sessionState.value)
        }
    }

    @Test
    fun loadingStateBlocksDoubleSubmit() {
        val gate = CompletableDeferred<Result<Unit>>()
        fake.onRegister = { _, _ -> gate.await() }
        val vm = viewModel()
        vm.fill()
        vm.submit()
        vm.submit()
        assertEquals(1, fake.registerCalls.size)
        gate.complete(Result.failure(AuthException(AuthError.EmailInUse)))
        assertFalse(vm.state.isLoading)
        assertEquals(RegisterError.Auth(AuthError.EmailInUse), vm.state.error)
    }

    @Test
    fun editingAFieldClearsTheError() {
        val vm = viewModel()
        vm.submit()
        assertEquals(RegisterError.EmptyFields, vm.state.error)
        vm.changeName("A")
        assertNull(vm.state.error)
        vm.submit()
        vm.changeConfirmPassword("x")
        assertNull(vm.state.error)
    }

    @Test
    fun profileIsRestoredButPasswordsAreNeverPersisted() {
        val saved = SavedStateHandle()
        viewModel(saved = saved).fill(phone = "3001234567")
        val restored = viewModel(saved = saved).state
        assertEquals("Ana Pérez", restored.name)
        assertEquals("1020304050", restored.document)
        assertEquals("3001234567", restored.phone)
        assertEquals("ana@unibanagro.com", restored.email)
        assertEquals("", restored.password)
        assertEquals("", restored.confirmPassword)
        assertTrue(saved.keys().none { saved.get<Any?>(it) == "secreto1" })
    }

    @Test
    fun exposesWhetherFirebaseIsConfigured() {
        assertTrue(viewModel().state.firebaseConfigured)
        assertFalse(viewModel(FakeAuthRepository(isConfigured = false)).state.firebaseConfigured)
    }
}
