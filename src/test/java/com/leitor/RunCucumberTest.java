package com.leitor;

import org.junit.runner.RunWith;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = "src/test/resources/features", // Caminho correto para as features
    glue = "com.leitor.steps", // Caminho correto para os pacotes contendo as definições dos steps
    plugin = {"pretty", "html:target/cucumber-reports"}, // Plugins para saída dos relatórios
    monochrome = true
)
public class RunCucumberTest {
}
