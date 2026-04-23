package dev.gavin.dialect

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DialectApplication

fun main(args: Array<String>) {
	runApplication<DialectApplication>(*args)
}
