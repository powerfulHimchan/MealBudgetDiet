package com.mealbudgetdiet;

import org.springframework.boot.SpringApplication;

public class TestMealBudgetDietApplication {

	public static void main(String[] args) {
		SpringApplication.from(MealBudgetDietApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
