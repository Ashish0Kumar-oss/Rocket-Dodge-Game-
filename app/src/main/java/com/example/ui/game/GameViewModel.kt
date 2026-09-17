package com.example.ui.game

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class GameStatus {
    START_SCREEN,
    PLAYING,
    GAME_OVER
}

data class Vector2(var x: Float, var y: Float)

data class Rocket(
    var position: Vector2,
    var velocity: Float = 0f,
    val width: Float = 50f,
    val height: Float = 90f
)

data class Obstacle(
    var position: Vector2,
    val radius: Float,
    val type: ObstacleType,
    val speed: Float,
    var horizontalSpeed: Float = 0f,
    var rotation: Float = 0f,
    val rotationSpeed: Float = 0f
)

enum class ObstacleType {
    ASTEROID,
    METEOR,
    SPACE_ROCK
}

data class Particle(
    var position: Vector2,
    var velocity: Vector2,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float
)

data class Star(
    var position: Vector2,
    val speed: Float,
    val size: Float,
    val alpha: Float
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("RocketDodge", Context.MODE_PRIVATE)
    
    private val _gameStatus = MutableStateFlow(GameStatus.START_SCREEN)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _highScore = MutableStateFlow(prefs.getInt("high_score", 0))
    val highScore: StateFlow<Int> = _highScore.asStateFlow()

    var screenWidth = 0f
    var screenHeight = 0f

    val rocket = Rocket(Vector2(0f, 0f))
    val obstacles = mutableListOf<Obstacle>()
    val particles = mutableListOf<Particle>()
    val stars = mutableListOf<Star>()

    private var gameLoopJob: Job? = null
    private var lastTime = 0L
    private var timeSurvived = 0f
    
    private var obstacleSpawnTimer = 0f
    private var obstacleSpawnInterval = 1.2f
    private var baseObstacleSpeed = 400f
    
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
        } catch (e: Exception) {
            // Tone generator might fail on some devices/emulators
        }
        initStars()
    }

    fun setScreenSize(width: Float, height: Float) {
        if (screenWidth == 0f && width > 0f) {
            screenWidth = width
            screenHeight = height
            rocket.position = Vector2(width / 2f, height - 250f)
            initStars()
        } else if (width > 0f) {
            screenWidth = width
            screenHeight = height
        }
    }

    private fun initStars() {
        stars.clear()
        if (screenWidth > 0 && screenHeight > 0) {
            for (i in 0..120) {
                stars.add(
                    Star(
                        position = Vector2(Random.nextFloat() * screenWidth, Random.nextFloat() * screenHeight),
                        speed = Random.nextFloat() * 150f + 50f,
                        size = Random.nextFloat() * 4f + 1f,
                        alpha = Random.nextFloat() * 0.6f + 0.2f
                    )
                )
            }
        }
    }

    fun startGame() {
        _gameStatus.value = GameStatus.PLAYING
        _score.value = 0
        timeSurvived = 0f
        obstacles.clear()
        particles.clear()
        rocket.position = Vector2(screenWidth / 2f, screenHeight - 250f)
        rocket.velocity = 0f
        obstacleSpawnInterval = 1.2f
        baseObstacleSpeed = 400f
        lastTime = System.currentTimeMillis()
        
        playSound(ToneGenerator.TONE_PROP_PROMPT)

        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (isActive && _gameStatus.value == GameStatus.PLAYING) {
                val currentTime = System.currentTimeMillis()
                val dt = (currentTime - lastTime) / 1000f
                lastTime = currentTime
                
                updateGame(dt)
                delay(16) // ~60fps
            }
        }
    }

    var isMovingLeft = false
    var isMovingRight = false

    fun setRocketTargetX(x: Float) {
        // Move towards target smoothly
        val diff = x - rocket.position.x
        rocket.velocity = diff * 15f // Proportional control
    }

    fun moveRocketLeft(moving: Boolean) {
        isMovingLeft = moving
    }

    fun moveRocketRight(moving: Boolean) {
        isMovingRight = moving
    }

    private fun updateGame(dt: Float) {
        if (dt > 0.1f) return // Prevent huge jumps if thread is paused
        
        timeSurvived += dt
        _score.value = (timeSurvived * 10).toInt()

        // Difficulty progression
        obstacleSpawnInterval = maxOf(0.25f, 1.2f - (timeSurvived * 0.015f))
        baseObstacleSpeed = minOf(1500f, 400f + (timeSurvived * 20f))

        // Handle Keyboard input movement
        if (isMovingLeft) {
            rocket.velocity -= 2000f * dt
        } else if (isMovingRight) {
            rocket.velocity += 2000f * dt
        }

        // Update Rocket
        rocket.position.x += rocket.velocity * dt
        // Apply friction
        rocket.velocity *= 0.8f
        
        // Bounds checking
        if (rocket.position.x < rocket.width / 2f) {
            rocket.position.x = rocket.width / 2f
            rocket.velocity = 0f
        }
        if (rocket.position.x > screenWidth - rocket.width / 2f) {
            rocket.position.x = screenWidth - rocket.width / 2f
            rocket.velocity = 0f
        }

        // Rocket Flame Particles
        if (Random.nextFloat() < 0.6f) {
            particles.add(
                Particle(
                    position = Vector2(rocket.position.x + (Random.nextFloat() - 0.5f) * 16f, rocket.position.y + rocket.height / 2f),
                    velocity = Vector2((Random.nextFloat() - 0.5f) * 40f, Random.nextFloat() * 300f + 200f),
                    life = 0f,
                    maxLife = 0.4f,
                    color = if (Random.nextBoolean()) Color(0xFFFF9900) else Color(0xFFFF3300),
                    size = Random.nextFloat() * 12f + 4f
                )
            )
        }

        // Update Stars
        stars.forEach { star ->
            star.position.y += star.speed * dt
            if (star.position.y > screenHeight) {
                star.position.y = 0f
                star.position.x = Random.nextFloat() * screenWidth
            }
        }

        // Spawn Obstacles
        obstacleSpawnTimer += dt
        if (obstacleSpawnTimer >= obstacleSpawnInterval) {
            obstacleSpawnTimer = 0f
            spawnObstacle()
        }

        // Update Obstacles
        val obstaclesToRemove = mutableListOf<Obstacle>()
        obstacles.forEach { obs ->
            obs.position.y += obs.speed * dt
            obs.position.x += obs.horizontalSpeed * dt
            obs.rotation += obs.rotationSpeed * dt
            
            // Bounce off walls if moving horizontally
            if (obs.position.x - obs.radius < 0 || obs.position.x + obs.radius > screenWidth) {
                obs.horizontalSpeed *= -1
            }
            
            if (obs.position.y - obs.radius > screenHeight) {
                obstaclesToRemove.add(obs)
            } else if (checkCollision(rocket, obs)) {
                gameOver()
            }
        }
        obstacles.removeAll(obstaclesToRemove)

        // Update Particles
        val particlesToRemove = mutableListOf<Particle>()
        particles.forEach { p ->
            p.position.x += p.velocity.x * dt
            p.position.y += p.velocity.y * dt
            p.life += dt
            if (p.life >= p.maxLife) {
                particlesToRemove.add(p)
            }
        }
        particles.removeAll(particlesToRemove)
    }

    private fun spawnObstacle() {
        val radius = Random.nextFloat() * 35f + 25f
        val type = ObstacleType.values()[Random.nextInt(ObstacleType.values().size)]
        val speedMult = Random.nextFloat() * 0.4f + 0.8f
        
        val horizontalSpeed = if (timeSurvived > 15f && Random.nextFloat() < 0.4f) {
            (Random.nextFloat() - 0.5f) * 250f
        } else 0f

        val rotationSpeed = (Random.nextFloat() - 0.5f) * 180f // Degrees per second

        obstacles.add(
            Obstacle(
                position = Vector2(Random.nextFloat() * screenWidth, -radius * 2),
                radius = radius,
                type = type,
                speed = baseObstacleSpeed * speedMult,
                horizontalSpeed = horizontalSpeed,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = rotationSpeed
            )
        )
    }

    private fun checkCollision(rocket: Rocket, obstacle: Obstacle): Boolean {
        // Simple bounding circle for obstacle and bounding box for rocket
        // Make the hit box slightly smaller than the visual size for fairness
        val hitBoxWidth = rocket.width * 0.7f
        val hitBoxHeight = rocket.height * 0.8f
        
        val closestX = obstacle.position.x.coerceIn(rocket.position.x - hitBoxWidth / 2f, rocket.position.x + hitBoxWidth / 2f)
        val closestY = obstacle.position.y.coerceIn(rocket.position.y - hitBoxHeight / 2f, rocket.position.y + hitBoxHeight / 2f)
        
        val distanceX = obstacle.position.x - closestX
        val distanceY = obstacle.position.y - closestY
        
        val hitRadius = obstacle.radius * 0.85f // Forgiving collision radius
        
        return (distanceX * distanceX + distanceY * distanceY) < (hitRadius * hitRadius)
    }

    private fun gameOver() {
        _gameStatus.value = GameStatus.GAME_OVER
        gameLoopJob?.cancel()
        
        playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT)

        // Explosion particles
        for (i in 0..80) {
            particles.add(
                Particle(
                    position = Vector2(rocket.position.x, rocket.position.y),
                    velocity = Vector2((Random.nextFloat() - 0.5f) * 1000f, (Random.nextFloat() - 0.5f) * 1000f),
                    life = 0f,
                    maxLife = Random.nextFloat() * 0.5f + 0.5f,
                    color = if (Random.nextBoolean()) Color.White else Color(0xFFFF5500),
                    size = Random.nextFloat() * 20f + 5f
                )
            )
        }

        if (_score.value > _highScore.value) {
            _highScore.value = _score.value
            prefs.edit().putInt("high_score", _score.value).apply()
        }
    }
    
    private fun playSound(toneType: Int) {
        try {
            toneGenerator?.startTone(toneType, 150)
        } catch (e: Exception) {
            // Ignore sound errors
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator?.release()
    }
}
