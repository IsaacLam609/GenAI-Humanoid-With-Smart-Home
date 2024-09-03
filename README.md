# Humanoid Speech Service Integration with Smart Elderly Home
(Refer to the [SpeechService](speechFrameworkDemo/src/main/java/com/ubtrobot/mini/speech/framework/demo/SpeechService.java) 
class for details of the coding implementation of this project.)<br>
This project is developed together with [Home Assistant OpenAI Integration](https://github.com/IsaacLam609/Home-Assistant-OpenAI-Integration/blob/main/README.md).
## Introduction
This project focuses on developing a speech service for the humanoid robot AlphaMini (embedded with the UBTech ROS Android system), 
enabling natural conversations with users and seamless control of smart home devices through the Home Assistant platform. 
The goal is to enhance the living experience of elderly individuals by providing companionship and assistance in managing their smart home environment.
The project utilizes Azure Speech Services and OpenAI Services for speech recognition, speech synthesis and advanced natural language processing.

## Features
- Natural language processing for conversational interactions
- Speech recognition (speech to text) and speech synthesis (text to speech)
- Stop playing audio response on a head tap
- Integration with Home Assistant for smart home device control
- News reporting summarized by large language models
- Calendar event creation and management
- Shopping list management

## Video Demonstration
(Eng) Controlling Smart Home Devices


https://github.com/user-attachments/assets/2e9e9296-93cd-4ba3-94fa-766e808e5eb9


(Eng) Weather Forecast


https://github.com/user-attachments/assets/51fc6eee-9077-4f3d-8d0e-31cdde4d745d


(Eng) Calendar Event Management


https://github.com/user-attachments/assets/f3c7bcb3-b269-4fff-8df3-31f9058d8921


(Chin) Controlling Smart Home Devices


https://github.com/user-attachments/assets/d8f2df37-f3d8-4954-8bb1-b4e9df0fc452


(Chin) Weather Forecast


https://github.com/user-attachments/assets/3d33999c-616d-4a40-a900-6ab9a4505896


(Chin) Calendar Event Management


https://github.com/user-attachments/assets/cd8710da-b4d2-490a-92d6-d4dc2a2a1379



## Usage
- Start the AlphaMini robot.
- Initiate a conversation by saying "Hey, Mini."
- Respond accordingly when the AlphaMini robot asks for confirmation before controlling smart home devices.
- The conversation session will end automatically after a duration a silence.

## Architecture
- **Components:**
  - **AlphaMini Robot**: The humanoid robot embedded with the UBTech ROS Android system.
  - **Home Assistant**: The platform for managing smart home devices.
  - **Speech Service**: The middleware facilitating communication between the AlphaMini robot and the user. 
    The built-in speech service is not used due to limited languages supported.

- **Flow:**
  1. User interacts verbally with AlphaMini.
  2. Speech recognition: AlphaMini sends the audio to Azure and gets the recognized text message.
  3. Natural language processing: AlphaMini sends the recognized text to Home Assistant for and gets the text response.
  4. Speech synthesis: AlphaMini sends the text response to Azure and plays the synthesized audio response.
  ![Flowchart](AlphaMini%20Flowchart.png)

## Acknowledgements
- [Project documentation](https://isaaclam609.github.io/GenAI-Humanoid-With-Smart-Home/)
- [AlphaMini SDK](https://docs.ubtrobot.com/alphamini/#/en-us/)
- [Home Assistant documentation](https://www.home-assistant.io/docs/)
- [Azure Speech Service documentation](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/)
- [Azure OpenAI Service documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/)
