/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2022 by Patrick Zedler and Dominic Zedler
 */

package xyz.zedler.patrick.grocy.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.android.volley.VolleyError;

import java.util.ArrayList;
import java.util.List;

import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.fragment.RecipeEditIngredientListFragmentArgs;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.FormDataRecipeEditIngredientList;
import xyz.zedler.patrick.grocy.model.InfoFullscreen;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.model.Recipe;
import xyz.zedler.patrick.grocy.model.RecipePosition;
import xyz.zedler.patrick.grocy.repository.RecipeEditRepository;
import xyz.zedler.patrick.grocy.Constants;

public class RecipeEditIngredientListViewModel extends BaseViewModel {

  private static final String TAG = RecipeEditIngredientListViewModel.class.getSimpleName();

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final RecipeEditRepository repository;
  private final FormDataRecipeEditIngredientList formData;
  private final RecipeEditIngredientListFragmentArgs args;

  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<InfoFullscreen> infoFullscreenLive;
  private final MutableLiveData<Boolean> offlineLive;

  private final String action;
  private final Recipe recipe;

  private ArrayList<RecipePosition> recipePositions;
  private List<Product> products;
  private List<QuantityUnit> quantityUnits;

  private final boolean isActionEdit;

  public RecipeEditIngredientListViewModel(
      @NonNull Application application,
      @NonNull RecipeEditIngredientListFragmentArgs startupArgs
  ) {
    super(application);

    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(application, TAG, isLoadingLive::setValue);
    grocyApi = new GrocyApi(application);
    repository = new RecipeEditRepository(application);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
    formData = new FormDataRecipeEditIngredientList(application, prefs, getBeginnerModeEnabled());
    args = startupArgs;
    isActionEdit = startupArgs.getAction().equals(Constants.ACTION.EDIT);

    infoFullscreenLive = new MutableLiveData<>();
    offlineLive = new MutableLiveData<>(false);

    action = args.getAction();
    recipe = args.getRecipe();
  }

  public FormDataRecipeEditIngredientList getFormData() {
    return formData;
  }

  public String getAction() {
    return args.getAction();
  }

  public boolean isActionEdit() {
    return isActionEdit;
  }

  public void loadFromDatabase(boolean downloadAfterLoading) {
    repository.loadFromDatabase(data -> {

      this.recipePositions = (ArrayList<RecipePosition>) RecipePosition.getRecipePositionsFromRecipeId(data.getRecipePositions(), recipe.getId());
      this.products = Product.getProductsForRecipePositions(data.getProducts(), recipePositions);
      this.quantityUnits = QuantityUnit.getQuantityUnitsForRecipePositions(data.getQuantityUnits(), recipePositions);

      formData.getRecipePositionsLive().setValue(recipePositions);
      formData.getProductsLive().setValue(products);
      formData.getQuantityUnitsLive().setValue(quantityUnits);
      if (downloadAfterLoading) {
        downloadData();
      }
    });
  }

  public void downloadData(@Nullable String dbChangedTime) {
    if (isOffline()) { // skip downloading
      isLoadingLive.setValue(false);
      return;
    }

    dlHelper.updateData(
            () -> loadFromDatabase(false),
            this::onDownloadError,
            RecipePosition.class,
            Product.class,
            QuantityUnit.class
    );
  }

  public void downloadData() {
    downloadData(null);
  }

  public void downloadDataForceUpdate() {
    SharedPreferences.Editor editPrefs = getSharedPrefs().edit();
    editPrefs.putString(Constants.PREF.DB_LAST_TIME_RECIPE_POSITIONS, null);
    editPrefs.putString(Constants.PREF.DB_LAST_TIME_PRODUCTS, null);
    editPrefs.putString(Constants.PREF.DB_LAST_TIME_QUANTITY_UNITS, null);
    editPrefs.apply();
    downloadData();
  }

  private void onDownloadError(@Nullable VolleyError error) {
    if (isDebuggingEnabled()) {
      Log.e(TAG, "onError: VolleyError: " + error);
    }
    showMessage(getString(R.string.msg_no_connection));
    if (!isOffline()) {
      setOfflineLive(true);
    }
  }

  @NonNull
  public MutableLiveData<Boolean> getOfflineLive() {
    return offlineLive;
  }

  public Boolean isOffline() {
    return offlineLive.getValue();
  }

  public void setOfflineLive(boolean isOffline) {
    offlineLive.setValue(isOffline);
  }

  @NonNull
  public MutableLiveData<Boolean> getIsLoadingLive() {
    return isLoadingLive;
  }

  @NonNull
  public MutableLiveData<InfoFullscreen> getInfoFullscreenLive() {
    return infoFullscreenLive;
  }

  public MutableLiveData<ArrayList<RecipePosition>> getRecipePositionsLive() {
    return formData.getRecipePositionsLive();
  }

  @Override
  protected void onCleared() {
    dlHelper.destroy();
    super.onCleared();
  }

  public Recipe getRecipe() {
    return recipe;
  }

  public ArrayList<RecipePosition> getRecipePositions() {
    return new ArrayList<>(recipePositions);
  }

  public ArrayList<Product> getProducts() {
    return new ArrayList<>(products);
  }

  public ArrayList<QuantityUnit> getQuantityUnits() {
    return new ArrayList<>(quantityUnits);
  }

  public void deleteRecipePosition(int recipePositionId) {
    dlHelper.delete(
            grocyApi.getObject(GrocyApi.ENTITY.RECIPES_POS, recipePositionId),
            response -> downloadData(),
            this::showErrorMessage
    );
  }

  public static class RecipeEditIngredientListViewModelFactory implements
      ViewModelProvider.Factory {

    private final Application application;
    private final RecipeEditIngredientListFragmentArgs args;

    public RecipeEditIngredientListViewModelFactory(
        Application application,
        RecipeEditIngredientListFragmentArgs args
    ) {
      this.application = application;
      this.args = args;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new RecipeEditIngredientListViewModel(application, args);
    }
  }
}
