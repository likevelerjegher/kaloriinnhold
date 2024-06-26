package com.likevel.kaloriinnhold.service;

import com.likevel.kaloriinnhold.exception.ObjectExistedException;
import com.likevel.kaloriinnhold.cache.CacheManager;
import com.likevel.kaloriinnhold.exception.ObjectNotFoundException;
import com.likevel.kaloriinnhold.model.Dish;
import com.likevel.kaloriinnhold.repositories.DishRepository;
import com.likevel.kaloriinnhold.services.DishService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DishServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private CacheManager<String, Object> cache;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private DishService dishService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetDishes_ReturnsListOfDishes() {
        // Arrange
        List<Dish> expectedDishes = new ArrayList<>();
        when(dishRepository.findAll()).thenReturn(expectedDishes);

        // Act
        List<Dish> dishes = dishService.getDishes();

        // Assert
        assertEquals(expectedDishes, dishes);
        verify(dishRepository, times(1)).findAll();
    }

    @Test
    void testGetDishById_ValidId_ReturnsDish() {
        // Arrange
        Long dishId = 1L;
        Dish expectedDish = new Dish();
        when(cache.get("dish 1")).thenReturn(null);
        when(dishRepository.findById(dishId)).thenReturn(Optional.of(expectedDish));

        // Act
        Dish dish = dishService.getDishById(dishId);

        // Assert
        assertEquals(expectedDish, dish);
        verify(cache, times(1)).get("dish 1");
        verify(dishRepository, times(1)).findById(dishId);
        verify(cache, times(1)).put("dish 1", expectedDish);
    }

    @Test
    void testGetDishById_CachedDataExists_ReturnsCachedDish() {
        // Arrange
        Long dishId = 1L;
        Dish expectedDish = new Dish();
        when(cache.get("dish 1")).thenReturn(expectedDish);

        // Act
        Dish dish = dishService.getDishById(dishId);

        // Assert
        assertEquals(expectedDish, dish);
        verify(cache, times(1)).get("dish 1");
        verify(dishRepository, times(0)).findById(anyLong());
    }
    @Test
    void testGetDishesWithLessOrSameCalories_ValidCalories_ReturnsListOfDishes() {
        // Arrange
        Integer calories = 500;
        List<Dish> expectedDishes = new ArrayList<>();
        when(dishRepository.getDishesWithLessOrSameCalories(calories)).thenReturn(expectedDishes);

        // Act
        List<Dish> dishes = dishService.getDishesWithLessOrSameCalories(calories);

        // Assert
        assertEquals(expectedDishes, dishes);
        verify(dishRepository, times(1)).getDishesWithLessOrSameCalories(calories);
    }

    @Test
    void testCreateNewDish_NonExistentDishName_CreatesNewDish() {
        // Arrange
        Dish dish = new Dish();
        dish.setDishName("New Dish");
        when(dishRepository.findDishByDishName(dish.getDishName())).thenReturn(Optional.empty());
        when(dishRepository.save(dish)).thenReturn(dish);

        // Act
        dishService.createNewDish(dish);

        // Assert
        verify(dishRepository, times(1)).findDishByDishName(dish.getDishName());
        verify(dishRepository, times(1)).save(dish);
        verify(cache, times(1)).put("dish " + dish.getId().toString(), dish);
    }

    @Test
    void testCreateNewDish_ExistentDishName_ThrowsObjectExistedException() {
        // Arrange
        Dish dish = new Dish();
        dish.setDishName("Existing Dish");
        when(dishRepository.findDishByDishName(dish.getDishName())).thenReturn(Optional.of(dish));

        // Act & Assert
        assertThrows(ObjectExistedException.class, () -> dishService.createNewDish(dish));
        verify(dishRepository, times(1)).findDishByDishName(dish.getDishName());
        verify(dishRepository, times(0)).save(any(Dish.class));
        verify(cache, times(0)).put(anyString(), any(Dish.class));
    }

    @Test
    @Transactional
    void testUpdateDish_ValidDishIdAndDishNameAndServings_UpdatesDish() {
        // Arrange
        Long dishId = 1L;
        String dishName = "Updated Dish";
        Float servings = 2.5f;
        Dish dish = new Dish();
        dish.setId(dishId);
        dish.setDishName("Original Dish");
        dish.setServings(1.0f);
        when(dishRepository.findById(dishId)).thenReturn(Optional.of(dish));
        when(dishRepository.findDishByDishName(dishName)).thenReturn(Optional.empty());

        // Act
        dishService.updateDish(dishId, dishName, servings);

        // Assert
        assertEquals(dishName, dish.getDishName());
        assertEquals(servings, dish.getServings());
        verify(dishRepository, times(1)).findById(dishId);
        verify(dishRepository, times(1)).findDishByDishName(dishName);
        verify(dishRepository, times(0)).save(any(Dish.class));
        verify(cache, times(1)).put("dish 1", dish);
    }

    @Test
    void testDeleteDish_ExistingDishId_DeletesDish() {
        // Arrange
        Long dishId = 1L;
        when(dishRepository.existsById(dishId)).thenReturn(true);

        // Act
        dishService.deleteDish(dishId);

        // Assert
        verify(dishRepository, times(1)).existsById(dishId);
        verify(dishRepository, times(1)).deleteById(dishId);
        verify(cache, times(1)).remove("dish 1");
    }

    @Test
    void testDeleteDish_NonExistentDishId_ThrowsObjectNotFoundException() {
        // Arrange
        Long dishId = 1L;
        when(dishRepository.existsById(dishId)).thenReturn(false);

        // Act & Assert
        assertThrows(ObjectNotFoundException.class, () -> dishService.deleteDish(dishId));
        verify(dishRepository, times(1)).existsById(dishId);
        verify(dishRepository, times(0)).deleteById(anyLong());
        verify(cache, times(0)).remove(anyString());
    }

    @Test
    void testDeleteDishes_DeletesAllDishes() {
        // Act
        dishService.deleteDishes();

        // Assert
        verify(dishRepository, times(1)).deleteAll();
        verify(cache, times(1)).clear();
    }
    @Test
    void testSaveDishes_WithNoExistingDishes_ShouldCreateDishes() {
        // Arrange
        List<Dish> dishes = new ArrayList<>();
        Dish dish1 = new Dish();
        dish1.setDishName("Dish 1");
        dish1.setId(1L);
        dishes.add(dish1);

        when(dishRepository.existsByDishName(anyString())).thenReturn(false);
        when(dishRepository.save(any(Dish.class))).thenReturn(dish1);

        // Act
        List<Dish> createdDishes = dishService.saveDishes(dishes);

        // Assert
        assertEquals(dishes, createdDishes);
        verify(dishRepository, times(1)).save(any(Dish.class));
    }
}