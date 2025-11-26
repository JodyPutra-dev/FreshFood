import tensorflow as tf
from tensorflow import keras
from keras import layers
import pathlib
import os

data_dir = pathlib.Path("fruit_ripeness_dataset/dataset")  # folder dataset/ berisi train/ & test/

train_dir = data_dir / "train"
test_dir = data_dir / "test"

img_size = (224, 224)
batch_size = 32

train_ds = keras.utils.image_dataset_from_directory(
    train_dir,
    image_size=img_size,
    batch_size=batch_size,
    shuffle=True
)

test_ds = keras.utils.image_dataset_from_directory(
    test_dir,
    image_size=img_size,
    batch_size=batch_size,
    shuffle=False
)

class_names = train_ds.class_names
print("Class :", class_names)

train_ds = train_ds.prefetch(buffer_size=tf.data.AUTOTUNE)
test_ds = test_ds.prefetch(buffer_size=tf.data.AUTOTUNE)


model = keras.Sequential([
    layers.Rescaling(1./255, input_shape=(224, 224, 3)),

    layers.Conv2D(32, 3, activation="relu"),
    layers.MaxPooling2D(),

    layers.Conv2D(64, 3, activation="relu"),
    layers.MaxPooling2D(),

    layers.Conv2D(128, 3, activation="relu"),
    layers.MaxPooling2D(),

    layers.Flatten(),
    layers.Dense(256, activation="relu"),
    layers.Dropout(0.5),
    layers.Dense(len(class_names), activation="softmax")
])

model.compile(
    optimizer="adam",
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"]
)

model.summary()
epochs = 15
history = model.fit(train_ds, validation_data=test_ds, epochs=epochs)

model.save("fruit_ripeness_model.keras")
model.save("fruit_ripeness_model.h5")

print("\nModel saved")


export_path = "saved_model"
model.export(export_path)
print("exported to:", export_path)


converter = tf.lite.TFLiteConverter.from_saved_model(export_path)
tflite_model = converter.convert()

with open("fruit_ripeness_model.tflite", "wb") as f:
    f.write(tflite_model)

print("TFLite model created: fruit_ripeness_model.tflite")


converter = tf.lite.TFLiteConverter.from_saved_model(export_path)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

quant_model = converter.convert()

with open("fruit_ripeness_model_int8.tflite", "wb") as f:
    f.write(quant_model)

print("TFLite model created: fruit_ripeness_model_int8.tflite")

with open("labels.txt", "w") as f:
    for label in class_names:
        f.write(label + "\n")

print("labels.txt created")
