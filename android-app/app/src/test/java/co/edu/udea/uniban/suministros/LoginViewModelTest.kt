package co.edu.udea.uniban.suministros

import androidx.lifecycle.SavedStateHandle
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.SessionState
import co.edu.udea.uniban.suministros.ui.auth.LoginError
import co.edu.udea.uniban.suministros.ui.auth.LoginViewModel
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
class LoginViewModelTest {
    private val fake = FakeAuthRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeAuthRepository = fake,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = LoginViewModel(repository, saved)

    private fun LoginViewModel.fill(email: String, password: String) {
        changeEmail(email)
        changePassword(password)
    }

    @Test
    fun emptyFieldsShowValidationErrorWithoutCallingRepository() {
        val vm = viewModel()
        vm.submit()
        assertEquals(LoginError.EmptyFields, vm.state.error)
        vm.changeEmail("ana@unibanagro.com")
        vm.submit()
        assertEquals(LoginError.EmptyFields, vm.state.error)
        assertTrue(fake.signInCalls.isEmpty())
    }

    @Test
    fun malformedEmailShowsFormatError() {
        val vm = viewModel()
        vm.fill("ana@", "secreto")
        vm.submit()
        assertEquals(LoginError.InvalidEmail, vm.state.error)
        assertTrue(fake.signInCalls.isEmpty())
    }

    @Test
    fun successCallsRepositoryWithTrimmedEmailAndOpensSession() {
        val vm = viewModel()
        vm.fill("  ana@unibanagro.com ", "secreto")
        vm.submit()
        assertEquals(listOf("ana@unibanagro.com" to "secreto"), fake.signInCalls)
        assertEquals(SessionState.Authenticated("uid-1", "ana@unibanagro.com"), fake.sessionState.value)
        assertNull(vm.state.error)
        // Sigue bloqueado hasta que la navegación abandone el login: evita el doble envío.
        assertTrue(vm.state.isLoading)
    }

    @Test
    fun everyAuthErrorIsExposedAndStopsLoading() {
        AuthError.entries.forEach { error ->
            val repo = FakeAuthRepository().apply { failWith(error) }
            val vm = viewModel(repo)
            vm.fill("ana@unibanagro.com", "secreto")
            vm.submit()
            assertEquals(LoginError.Auth(error), vm.state.error)
            assertFalse(vm.state.isLoading)
        }
    }

    @Test
    fun loadingStateBlocksDoubleSubmit() {
        val gate = CompletableDeferred<Result<Unit>>()
        fake.onSignIn = { _, _ -> gate.await() }
        val vm = viewModel()
        vm.fill("ana@unibanagro.com", "secreto")
        vm.submit()
        assertTrue(vm.state.isLoading)
        vm.submit()
        assertEquals(1, fake.signInCalls.size)
        gate.complete(Result.failure(co.edu.udea.uniban.suministros.data.auth.AuthException(AuthError.Network)))
        assertFalse(vm.state.isLoading)
        assertEquals(LoginError.Auth(AuthError.Network), vm.state.error)
    }

    @Test
    fun editingAFieldClearsTheError() {
        val vm = viewModel()
        vm.submit()
        assertEquals(LoginError.EmptyFields, vm.state.error)
        vm.changeEmail("a")
        assertNull(vm.state.error)
        vm.submit()
        assertEquals(LoginError.EmptyFields, vm.state.error)
        vm.changePassword("x")
        assertNull(vm.state.error)
    }

    @Test
    fun emailIsRestoredButPasswordIsNeverPersisted() {
        val saved = SavedStateHandle()
        val first = viewModel(saved = saved)
        first.fill("ana@unibanagro.com", "secreto")
        val restored = viewModel(saved = saved)
        assertEquals("ana@unibanagro.com", restored.state.email)
        assertEquals("", restored.state.password)
        assertTrue(saved.keys().none { saved.get<Any?>(it) == "secreto" })
    }

    @Test
    fun togglePasswordVisibility() {
        val vm = viewModel()
        assertFalse(vm.state.passwordVisible)
        vm.togglePasswordVisible()
        assertTrue(vm.state.passwordVisible)
    }

    @Test
    fun demoOnlyWhenRepositoryAllowsIt() {
        val noDemo = viewModel()
        noDemo.continueAsDemo()
        assertEquals(SessionState.Unauthenticated, fake.sessionState.value)

        val demoRepo = FakeAuthRepository(isConfigured = false, demoAvailable = true)
        val vm = viewModel(demoRepo)
        assertTrue(vm.state.demoAvailable)
        assertFalse(vm.state.firebaseConfigured)
        vm.continueAsDemo()
        assertEquals(SessionState.Authenticated("demo", null), demoRepo.sessionState.value)
    }
}
