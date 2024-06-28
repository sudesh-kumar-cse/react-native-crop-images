
import { NativeModules } from "react-native";
const { CropImage } = NativeModules;
module.exports = {
    configure: CropImage.configure,
    captureImage: CropImage.captureImage,
    pickImage: CropImage.pickImage,
}