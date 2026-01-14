package com.example.bookly.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookly.ui.screens.LoginScreen
import com.example.bookly.ui.screens.WelcomeScreen

object Routes{
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

@Composable
fun BooklyNavigation (){
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME
    ){
        composable(Routes.WELCOME){
            WelcomeScreen(
                onStartReading = {
                    navController.navigate(Routes.LOGIN)
                }
            )
        }
        composable(Routes.LOGIN){
            LoginScreen(
                onLoginSuccess = {
                    //TODO: catre Home
                    println("Login successful!")
                },
                onNavigateToSignUp = {
                    //TODO: catre Register
                    println("Navigate to Sign Up")
                },
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }
    }
}