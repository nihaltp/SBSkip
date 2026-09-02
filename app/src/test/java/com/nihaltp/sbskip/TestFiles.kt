package com.nihaltp.sbskip

import java.io.File

object TestFiles {
    val generatedFixturesDir =
        File("build/test-fixtures").apply {
            mkdirs()
        }
}
