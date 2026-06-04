package app.freerouting.geometry.planar

/**
 * Enum for the eight 45-degree direction starting from right in counterclocksense to down45.
 */
enum class FortyfiveDegreeDirection {
    RIGHT {
        override fun get_direction(): IntDirection = Direction.RIGHT
    },
    RIGHT45 {
        override fun get_direction(): IntDirection = Direction.RIGHT45
    },
    UP {
        override fun get_direction(): IntDirection = Direction.UP
    },
    UP45 {
        override fun get_direction(): IntDirection = Direction.UP45
    },
    LEFT {
        override fun get_direction(): IntDirection = Direction.LEFT
    },
    LEFT45 {
        override fun get_direction(): IntDirection = Direction.LEFT45
    },
    DOWN {
        override fun get_direction(): IntDirection = Direction.DOWN
    },
    DOWN45 {
        override fun get_direction(): IntDirection = Direction.DOWN45
    };

    abstract fun get_direction(): IntDirection
}
