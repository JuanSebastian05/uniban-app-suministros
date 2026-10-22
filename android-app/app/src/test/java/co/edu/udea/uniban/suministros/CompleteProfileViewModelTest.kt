package co.edu.udea.uniban.suministros

import androidx.lifecycle.SavedStateHandle
import co.edu.udea.uniban.suministros.data.auth.AuthError
import co.edu.udea.uniban.suministros.data.auth.AuthException
import co.edu.udea.uniban.suministros.data.auth.ProfileState
import co.edu.udea.uniban.suministros.data.auth.SessionState
import co.edu.udea.uniban.suministros.ui.auth.CompleteProfileError
import co.edu.udea.uniban.suministros.ui.auth.CompleteProfileViewModel
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

/** Completar el perfil de una cuenta sin perfil en el backend (P-02). */
@OptIn(ExperimentalCoroutinesApi::class)
class CompleteProfileViewModelTest {
    private val fake = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com"))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeAuthRepository = fake,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = CompleteProfileViewModel(repository, saved)

    private fun CompleteProfileViewModel.fill(
        name: String = "Ana Pérez",
        document: String = "1020304050",
        phone: String = "",
    ) {
        changeName(name)
        changeDocument(document)
        changePhone(phone)
    }

    @Test
    fun showsTheEmailOfTheOpenSession() {
        assertEquals("ana@unibanagro.com", viewModel().state.email)
        assertEquals("", viewModel(FakeAuthRepository(SessionState.Authenticated("uid-2", null))).state.email)
    }

    @Test
    fun requiredFieldsAreValidatedWithoutCallingRepository() {
        val empty = viewModel().apply { submit() }
        assertEquals(CompleteProfileError.EmptyFields, empty.state.error)
        assertEquals(CompleteProfileError.EmptyFields, viewModel().apply { fill(name = "  "); submit() }.state.error)
        assertEquals(CompleteProfileError.EmptyFields, viewModel().apply { fill(document = ""); submit() }.state.error)
        assertTrue(fake.completeProfileCalls.isEmpty())
    }

    @Test
    fun successSendsTrimmedDataAndLeavesTheProfileComplete() {
        val vm = viewModel()
        vm.fill(name = " Ana Pérez ", document = " 1020304050 ", phone = "  ")
        vm.submit()
        assertEquals(listOf(Triple("Ana Pérez", "1020304050", null)), fake.completeProfileCalls)
        assertEquals(ProfileState.Complete, fake.profileState.value)
        assertNull(vm.state.error)
        // La navegación reacciona al perfil completo; el botón sigue bloqueado hasta salir de la pantalla.
        assertTrue(vm.state.isLoading)
    }

    @Test
    fun phoneIsSentWhenPresent() {
        viewModel().apply { fill(phone = "3001234567"); submit() }
        assertEquals("3001234567", fake.completeProfileCalls.single().third)
    }

    @Test
    fun failuresAreExposedAndStopLoading() {
        listOf(AuthError.Network, AuthError.DocumentInUse, AuthError.Server, AuthError.Unknown).forEach { error ->
            val repo = FakeAuthRepository(SessionState.Authenticated("uid-1", "ana@unibanagro.com")).apply {
                onCompleteProfile = { _, _, _ -> Result.failure(AuthException(error)) }
            }
            val vm = viewModel(repo)
            vm.fill()
            vm.submit()
            assertEquals(CompleteProfileError.Auth(error), vm.state.error)
            assertFalse(vm.state.isLoading)
            assertEquals(ProfileState.Unknown, repo.profileState.value)
        }
    }

    @Test
    fun loadingStateBlocksDoubleSubmit() {
        val gate = CompletableDeferred<Result<Unit>>()
        fake.onCompleteProfile = { _, _, _ -> gate.await() }
        val vm = viewModel()
        vm.fill()
        vm.submit()
        vm.submit()
        assertEquals(1, fake.completeProfileCalls.size)
        gate.complete(Result.failure(AuthException(AuthError.DocumentInUse)))
        assertFalse(vm.state.isLoading)
        assertEquals(CompleteProfileError.Auth(AuthError.DocumentInUse), vm.state.error)
    }

    @Test
    fun editingAFieldClearsTheError() {
        val vm = viewModel()
        vm.submit()
        assertEquals(CompleteProfileError.EmptyFields, vm.state.error)
        vm.changeName("A")
        assertNull(vm.state.error)
    }

    @Test
    fun enteredDataSurvivesProcessRecreation() {
        val saved = SavedStateHandle()
        viewModel(saved = saved).fill(phone = "3001234567")
        val restored = viewModel(saved = saved).state
        assertEquals("Ana Pérez", restored.name)
        assertEquals("1020304050", restored.document)
        assertEquals("3001234567", restored.phone)
    }

    @Test
    fun signOutDelegatesToTheRepository() {
        viewModel().signOut()
        assertEquals(1, fake.signOutCalls)
        assertEquals(SessionState.Unauthenticated, fake.sessionState.value)
    }
}
