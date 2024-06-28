# react-native-crop-images


[![Download](https://img.shields.io/badge/Download-v0.0.2-ff69b4.svg) ](https://www.npmjs.com/package/react-native-app-splash)

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

Use like so:

```javascript
import React from 'react'
import { StyleSheet, Text, View, Image, Pressable } from 'react-native'
import CropImage from 'react-native-crop-image';


CropImage.configure({
    cropType: 'circular',
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

## Contribution

Issues are welcome. Please add a screenshot of you bug and a code snippet. Quickest way to solve issue is to reproduce it in one of the examples.
---

**[MIT Licensed](https://github.com/sudesh-kumar-cse/react-native-crop-images/blob/master/LICENSE)**