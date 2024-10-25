# react-native-crop-images


[![Download](https://img.shields.io/badge/Download-v0.0.11-ff69b4.svg) ](https://www.npmjs.com/package/react-native-crop-images)

React Native app image cropping library 
# Image Cropping Module for React Native

This module provides a comprehensive solution for image cropping within React Native applications, specifically tailored for the Android & iOS platform.


## Content

- [Installation](#installation)
- [Getting started](#getting-started)
- [Troubleshooting](#troubleshooting)
- [Contribution](#contribution)





## Installation

### First Step(Download):

```bash
$ npm install --save react-native-crop-images
```
```bash
$ yarn add react-native-crop-images
```


#### Manual Installation(If Needed)

**Android:**

1. In your `android/settings.gradle` file, make the following additions:
```java or kotlin
include ':react-native-crop-images'   
project(':react-native-crop-images').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-crop-images/android')
```

2. In your android/app/build.gradle file, add the `:react-native-crop-images` project as a compile-time dependency:

```java or kotlin
...
dependencies {
    ...
    implementation project(':react-native-crop-images')
}
```

**iOS:**

1. `cd ios`
2. `run pod install`



## Getting started  

## Usage

### Configuration
```javascript
CropImage.configure({
    cropType: 'rectangular', //circular or rectangular
    freeStyleCropEnabled: true, // true or false
    showCropFrame: true, // true or false
    showCropGrid: true, // true or false
    dimmedLayerColor: '#99000000', // any color
    imageQuality: 80, // integer, 60-100
});
```


### Pick Image

```javascript
CropImage.pickImage()
        .then(uri => {
             console.log(uri);
        })
        .catch(error => console.error(error));
```


### Capture Image

```javascript
 CropImage.captureImage()
            .then(uri => {
                console.log(uri);
            })
            .catch(error => console.error(error));
```

### Sample Code
```javascript
import React from 'react'
import { StyleSheet, Text, View, Image, Pressable } from 'react-native'
import CropImage from 'react-native-crop-image';


CropImage.configure({
    cropType: 'rectangular', //circular or rectangular
    freeStyleCropEnabled: true, // true or false
    showCropFrame: true, // true or false
    showCropGrid: true, // true or false
    dimmedLayerColor: '#99000000', // any color 
    imageQuality: '80', // between 60 to 100
});

const App = () => {
    const [croppedImageUri, setCroppedImageUri] = React.useState(null);
    const handleOpenImageCrop = () => {
        CropImage.pickImage()
            .then(uri => {
                console.log(uri);
                setCroppedImageUri(uri);
            })
            .catch(error => console.error(error));
    }

    const handleCaptureImageCrop = () => {
        CropImage.captureImage()
            .then(uri => {
                console.log(uri);
                setCroppedImageUri(uri);
            })
            .catch(error => console.error(error));
    }

    return (
        <View>
            <Pressable style={{ margin: 10, padding: 10, borderRadius: 10, backgroundColor: '#3572EF', alignItems: 'center', justifyContent: 'center' }} onPress={handleOpenImageCrop} >
                <Text style={{ color: 'white' }}>Open Image Crop</Text>
            </Pressable>
            <Pressable style={{ margin: 10, padding: 10, borderRadius: 10, backgroundColor: '#3572EF', alignItems: 'center', justifyContent: 'center' }} onPress={handleCaptureImageCrop} >
                <Text style={{ color: 'white' }}>Capture Image Crop</Text>
            </Pressable>
            {croppedImageUri && <Image source={{ uri: croppedImageUri }} style={styles.image} />}
        </View>
    )
}

export default App

const styles = StyleSheet.create({
    image: {
        width: 200,
        height: 200,
        resizeMode: 'contain'
    },
})
```



### Configuration Object

| Property             |             Type             | Description                                                                                                                                                                                                                                               |
| -------------------- | :--------------------------: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| cropType             | string (default rectangular) | The `cropType` property of the image cropping library specifies the type of cropping area used. It can be either 'rectangular' or 'circular'. This property determines the shape of the selectable crop area when using the library's crop functionality. |
| freeStyleCropEnabled |     bool (default true)      | When `freeStyleCropEnabled` is set to true, users can freely adjust the cropping frame according to their preference, allowing for non-fixed cropping dimensions.                                                                                         |
| showCropFrame        |     bool (default true)      | When `showCropFrame` is set to true, the crop frame lines are displayed in the crop view, aiding users in adjusting the cropping area.                                                                                                                    |
| showCropGrid         |     bool (default true)      | When `showCropGrid` is set to true, a grid is displayed within the crop frame, providing a visual aid for precise cropping.                                                                                                                               |
| dimmedLayerColor     |  any (default `#99000000`)   | Specifies the color used to dim the background behind the crop image UI. You can use direct color names (e.g., "black") or specify colors using an ARGB hexadecimal format (e.g., #99000000 for semi-transparent black).                                  |
|                      |
| imageQuality     |  integer (default `60`)   | When `imageQuality` is set to an integer between 60 (lower quality) and 100 (highest quality), it determines the quality of the image.
.                                  |
|                      |



### Response String

| Property |  Type  | Description                                                                                                   |
| -------- | :----: | :------------------------------------------------------------------------------------------------------------ |
| uri      | string | The URI of the selected image returned by the function `CropImage.pickImage()` or `CropImage.captureImage()`. |
|          |




## Contribution

Issues are welcome. Please add a screenshot of you bug and a code snippet. Quickest way to solve issue is to reproduce it in one of the examples.
---

**[MIT Licensed](https://github.com/sudesh-kumar-cse/react-native-crop-images/blob/master/LICENSE)**