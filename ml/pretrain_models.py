"""
Pre-train and save all ML models to disk for instant loading in the Streamlit app
Run this script once to train and save models, then the Streamlit app will load them instantly
"""

import pandas as pd
import numpy as np
import os
import joblib
import time
from sklearn.tree import DecisionTreeClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import LabelEncoder, StandardScaler
from tqdm import tqdm


def optimize_dtypes(df):
    """Reduce memory usage by downcasting numeric columns."""
    for col in df.select_dtypes(include=["int64"]).columns:
        df[col] = pd.to_numeric(df[col], downcast="integer")
    for col in df.select_dtypes(include=["float64"]).columns:
        df[col] = pd.to_numeric(df[col], downcast="float")
    return df

def create_models_dir():
    """Create models directory if it doesn't exist"""
    models_dir = "models"
    if not os.path.exists(models_dir):
        os.makedirs(models_dir)
        print(f"✅ Created 'models' directory")
    return models_dir

def load_and_preprocess_data():
    """Load training data and preprocess it"""
    print("\n📊 Loading and preprocessing data...")
    
    # Load data
    df = pd.read_csv("dataset/train_cleaned.csv")
    print(f"✅ Loaded {len(df):,} records with {len(df.columns)} features")
    
    # Prepare features and target
    X_train = df.drop('position', axis=1)
    y_train = df['position']

    # Reduce memory footprint before model training
    X_train = optimize_dtypes(X_train)
    
    print(f"✅ Features: {len(X_train.columns)}, Target: position")
    
    # Identify and encode categorical columns
    categorical_cols = X_train.select_dtypes(include=['object']).columns.tolist()
    print(f"✅ Found {len(categorical_cols)} categorical features: {categorical_cols}")
    
    label_encoders = {}
    for col in tqdm(categorical_cols, desc="Encoding categorical features"):
        le = LabelEncoder()
        X_train[col] = le.fit_transform(X_train[col].astype(str))
        label_encoders[col] = le
    
    print(f"✅ Encoding completed")
    
    return X_train, y_train, label_encoders


def train_random_forest_memory_safe(X_train, y_train):
    """Train RandomForest with fallback configs to avoid OOM on large datasets."""
    # Start with stronger model, then fallback to lighter configs if memory is insufficient.
    fallback_configs = [
        {"n_estimators": 60, "max_depth": 18, "min_samples_leaf": 2, "sample_size": 500_000},
        {"n_estimators": 40, "max_depth": 16, "min_samples_leaf": 3, "sample_size": 300_000},
        {"n_estimators": 25, "max_depth": 14, "min_samples_leaf": 5, "sample_size": 200_000},
    ]

    for i, cfg in enumerate(fallback_configs, start=1):
        try:
            sample_size = min(cfg["sample_size"], len(X_train))
            if sample_size < len(X_train):
                print(f"   Attempt {i}: training on sampled subset ({sample_size:,} rows)...")
                sampled_idx = np.random.RandomState(42).choice(len(X_train), size=sample_size, replace=False)
                X_rf = X_train.iloc[sampled_idx]
                y_rf = y_train.iloc[sampled_idx]
            else:
                print(f"   Attempt {i}: training on full dataset ({sample_size:,} rows)...")
                X_rf = X_train
                y_rf = y_train

            rf_model = RandomForestClassifier(
                random_state=42,
                n_jobs=1,
                n_estimators=cfg["n_estimators"],
                max_depth=cfg["max_depth"],
                min_samples_leaf=cfg["min_samples_leaf"],
            )
            rf_model.fit(X_rf, y_rf)
            print(
                f"✅ Random Forest trained with n_estimators={cfg['n_estimators']}, "
                f"max_depth={cfg['max_depth']}, sample_size={sample_size:,}"
            )
            return rf_model

        except MemoryError:
            print(f"⚠️  Attempt {i} failed due to memory limits. Trying a smaller configuration...")
        except Exception as exc:
            if "could not allocate" in str(exc).lower() or "memory" in str(exc).lower():
                print(f"⚠️  Attempt {i} failed due to memory limits. Trying a smaller configuration...")
            else:
                raise

    raise RuntimeError("Random Forest training failed after all memory-safe fallback attempts.")


def train_logistic_regression_memory_safe(X_train, y_train):
    """Train Logistic Regression with sampled fallback to avoid long runtimes/OOM."""
    fallback_configs = [
        {"sample_size": 50_000, "max_iter": 120, "tol": 1e-2},
        {"sample_size": 30_000, "max_iter": 100, "tol": 1e-2},
        {"sample_size": 20_000, "max_iter": 80, "tol": 5e-2},
    ]

    for i, cfg in enumerate(fallback_configs, start=1):
        try:
            sample_size = min(cfg["sample_size"], len(X_train))
            if sample_size < len(X_train):
                print(f"   Attempt {i}: training on sampled subset ({sample_size:,} rows)...")
                sampled_idx = np.random.RandomState(42).choice(len(X_train), size=sample_size, replace=False)
                X_lr = X_train.iloc[sampled_idx]
                y_lr = y_train.iloc[sampled_idx]
            else:
                print(f"   Attempt {i}: training on full dataset ({sample_size:,} rows)...")
                X_lr = X_train
                y_lr = y_train

            scaler = StandardScaler()
            X_lr_scaled = scaler.fit_transform(X_lr)

            lr_model = LogisticRegression(
                random_state=42,
                max_iter=cfg["max_iter"],
                solver="saga",
                tol=cfg["tol"],
            )
            lr_model.fit(X_lr_scaled, y_lr)
            print(
                f"✅ Logistic Regression trained with sample_size={sample_size:,}, "
                f"max_iter={cfg['max_iter']}"
            )
            return lr_model, scaler

        except MemoryError:
            print(f"⚠️  Attempt {i} failed due to memory limits. Trying a smaller configuration...")
        except Exception as exc:
            if "could not allocate" in str(exc).lower() or "memory" in str(exc).lower():
                print(f"⚠️  Attempt {i} failed due to memory limits. Trying a smaller configuration...")
            else:
                raise

    raise RuntimeError("Logistic Regression training failed after all fallback attempts.")

def train_and_save_models():
    """Train all models and save them to disk"""
    
    print("\n" + "="*60)
    print("🏎️  F1nalyze Model Pre-training Script")
    print("="*60)
    
    # Create models directory
    models_dir = create_models_dir()
    
    # Load and preprocess data
    X_train, y_train, label_encoders = load_and_preprocess_data()
    
    print("\n🤖 Training Models...")
    print("-"*60)
    
    # Train Decision Tree
    print("\n1️⃣  Training Decision Tree Classifier...")
    start_time = time.time()
    dt_model = DecisionTreeClassifier(random_state=42)
    dt_model.fit(X_train, y_train)
    dt_time = time.time() - start_time
    print(f"✅ Decision Tree trained in {dt_time:.2f}s")
    
    # Train Random Forest
    print("\n2️⃣  Training Random Forest Classifier...")
    start_time = time.time()
    rf_model = train_random_forest_memory_safe(X_train, y_train)
    rf_time = time.time() - start_time
    print(f"✅ Random Forest trained in {rf_time:.2f}s")
    
    # Scale data and train Logistic Regression
    print("\n3️⃣  Training Logistic Regression...")
    start_time = time.time()
    lr_model, scaler = train_logistic_regression_memory_safe(X_train, y_train)
    lr_time = time.time() - start_time
    print(f"✅ Logistic Regression trained in {lr_time:.2f}s")
    
    total_time = dt_time + rf_time + lr_time
    print(f"\n⏱️  Total training time: {total_time:.2f}s")
    
    # Save models to disk
    print("\n💾 Saving models to disk...")
    print("-"*60)
    
    try:
        joblib.dump(dt_model, os.path.join(models_dir, "decision_tree_model.pkl"))
        print(f"✅ Decision Tree model saved")
        
        joblib.dump(rf_model, os.path.join(models_dir, "random_forest_model.pkl"))
        print(f"✅ Random Forest model saved")
        
        joblib.dump(lr_model, os.path.join(models_dir, "logistic_regression_model.pkl"))
        print(f"✅ Logistic Regression model saved")
        
        joblib.dump(scaler, os.path.join(models_dir, "scaler.pkl"))
        print(f"✅ Scaler saved")
        
        joblib.dump(label_encoders, os.path.join(models_dir, "label_encoders.pkl"))
        print(f"✅ Label encoders saved")
        
        joblib.dump(X_train.columns.tolist(), os.path.join(models_dir, "feature_names.pkl"))
        print(f"✅ Feature names saved")
        
        print("\n" + "="*60)
        print("🎉 All models pre-trained and saved successfully!")
        print("="*60)
        print("\n📝 Next steps:")
        print("   Run: streamlit run app.py")
        print("   The app will instantly load all pre-trained models!")
        print("\n")
        
    except Exception as e:
        print(f"\n❌ Error saving models: {str(e)}")
        return False
    
    return True

if __name__ == "__main__":
    try:
        success = train_and_save_models()
        exit(0 if success else 1)
    except Exception as e:
        print(f"\n❌ Error during pre-training: {str(e)}")
        exit(1)
