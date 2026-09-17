package com.example.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val viewModel: GameViewModel = viewModel()
    val gameStatus by viewModel.gameStatus.collectAsState()
    val score by viewModel.score.collectAsState()
    val highScore by viewModel.highScore.collectAsState()
    
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070014)) // Deep dark space background
            .onSizeChanged { size ->
                viewModel.setScreenSize(size.width.toFloat(), size.height.toFloat())
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (gameStatus != GameStatus.PLAYING) return@onKeyEvent false
                
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft, Key.A -> {
                            viewModel.moveRocketLeft(true)
                            true
                        }
                        Key.DirectionRight, Key.D -> {
                            viewModel.moveRocketRight(true)
                            true
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                     when (event.key) {
                        Key.DirectionLeft, Key.A -> {
                            viewModel.moveRocketLeft(false)
                            true
                        }
                        Key.DirectionRight, Key.D -> {
                            viewModel.moveRocketRight(false)
                            true
                        }
                        else -> false
                     }
                } else {
                    false
                }
            }
    ) {
        // Main Game Canvas
        GameCanvas(viewModel)
        
        // HUD & Screens
        when (gameStatus) {
            GameStatus.START_SCREEN -> StartScreen(highScore) { viewModel.startGame() }
            GameStatus.PLAYING -> HUD(score, highScore)
            GameStatus.GAME_OVER -> GameOverScreen(score, highScore) { viewModel.startGame() }
        }
    }
}

@Composable
fun GameCanvas(viewModel: GameViewModel) {
    val rocket = viewModel.rocket
    val obstacles = viewModel.obstacles
    val particles = viewModel.particles
    val stars = viewModel.stars
    val gameStatus by viewModel.gameStatus.collectAsState()
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (gameStatus == GameStatus.PLAYING) {
                        viewModel.setRocketTargetX(change.position.x)
                    }
                }
            }
    ) {
        // Draw Stars
        for (star in stars) {
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.size,
                center = Offset(star.position.x, star.position.y)
            )
        }
        
        // Draw Particles
        for (p in particles) {
            val alpha = (1f - (p.life / p.maxLife)).coerceIn(0f, 1f)
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.size,
                center = Offset(p.position.x, p.position.y)
            )
        }
        
        // Draw Obstacles
        for (obs in obstacles) {
            drawObstacle(obs)
        }
        
        // Draw Rocket
        if (gameStatus != GameStatus.GAME_OVER) {
            drawRocket(rocket)
        }
    }
}

fun DrawScope.drawObstacle(obs: Obstacle) {
    val center = Offset(obs.position.x, obs.position.y)
    rotate(obs.rotation, center) {
        when (obs.type) {
            ObstacleType.ASTEROID -> {
                drawCircle(color = Color(0xFF888888), radius = obs.radius, center = center)
                drawCircle(color = Color(0xFF666666), radius = obs.radius * 0.4f, center = center.copy(x = center.x - obs.radius * 0.3f, y = center.y - obs.radius * 0.3f))
                drawCircle(color = Color(0xFF555555), radius = obs.radius * 0.25f, center = center.copy(x = center.x + obs.radius * 0.4f, y = center.y + obs.radius * 0.2f))
            }
            ObstacleType.METEOR -> {
                val path = Path().apply {
                    moveTo(center.x, center.y - obs.radius)
                    lineTo(center.x + obs.radius * 0.8f, center.y - obs.radius * 0.2f)
                    lineTo(center.x + obs.radius, center.y + obs.radius * 0.6f)
                    lineTo(center.x + obs.radius * 0.3f, center.y + obs.radius)
                    lineTo(center.x - obs.radius * 0.7f, center.y + obs.radius * 0.5f)
                    lineTo(center.x - obs.radius, center.y - obs.radius * 0.3f)
                    close()
                }
                drawPath(path, color = Color(0xFFAA4433))
            }
            ObstacleType.SPACE_ROCK -> {
                drawCircle(color = Color(0xFF445577), radius = obs.radius, center = center)
                val rectSize = Size(obs.radius * 1.2f, obs.radius * 1.2f)
                drawRect(
                    color = Color(0xFF334466),
                    topLeft = Offset(center.x - rectSize.width / 2, center.y - rectSize.height / 2),
                    size = rectSize,
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}

fun DrawScope.drawRocket(rocket: Rocket) {
    val center = Offset(rocket.position.x, rocket.position.y)
    val width = rocket.width
    val height = rocket.height
    
    // Tilt rocket based on velocity
    val tiltAngle = (rocket.velocity / 30f).coerceIn(-30f, 30f)
    
    rotate(tiltAngle, center) {
        // Main Body (Metallic)
        val bodyPath = Path().apply {
            moveTo(center.x, center.y - height / 2) // Nose
            quadraticTo(center.x + width / 2, center.y - height / 4, center.x + width / 2, center.y + height / 3) // Right side
            lineTo(center.x - width / 2, center.y + height / 3) // Bottom
            quadraticTo(center.x - width / 2, center.y - height / 4, center.x, center.y - height / 2) // Left side
            close()
        }
        drawPath(bodyPath, color = Color(0xFFE0E0E0))
        
        // Window
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = width * 0.25f,
            center = center.copy(y = center.y - height * 0.1f)
        )
        drawCircle(
            color = Color.White,
            radius = width * 0.25f,
            center = center.copy(y = center.y - height * 0.1f),
            style = Stroke(width = 3f)
        )
        
        // Fins
        val finPath = Path().apply {
            // Left fin
            moveTo(center.x - width * 0.4f, center.y + height * 0.1f)
            lineTo(center.x - width * 0.8f, center.y + height * 0.5f)
            lineTo(center.x - width * 0.3f, center.y + height * 0.3f)
            // Right fin
            moveTo(center.x + width * 0.4f, center.y + height * 0.1f)
            lineTo(center.x + width * 0.8f, center.y + height * 0.5f)
            lineTo(center.x + width * 0.3f, center.y + height * 0.3f)
        }
        drawPath(finPath, color = Color(0xFFFF3366))
    }
}

@Composable
fun HUD(score: Int, highScore: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(top = 24.dp), // Safe area inset approx
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("SCORE: $score", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("HIGH: $highScore", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StartScreen(highScore: Int, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ROCKET DODGE", color = Color(0xFF00E5FF), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Move left and right to dodge the obstacles!", color = Color.White, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        if (highScore > 0) {
            Text("High Score: $highScore", color = Color.Yellow, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(32.dp))
        }
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366))
        ) {
            Text("PLAY", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun GameOverScreen(score: Int, highScore: Int, onRestart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("GAME OVER", color = Color.Red, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Final Score: $score", color = Color.White, fontSize = 24.sp)
        Text("Best Score: $highScore", color = Color.Yellow, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
        ) {
            Text("RESTART", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }
    }
}
