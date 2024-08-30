# Humanoid Speech Service Integration with Smart Elderly Home
(Refer to the [SpeechService](speechFrameworkDemo/src/main/java/com/ubtrobot/mini/speech/framework/demo/SpeechService.java) 
class for details of the coding implementation.)
## Introduction
This project focuses on developing a speech service for the humanoid robot AlphaMini, 
enabling natural conversations with users and seamless control of smart home devices through the Home Assistant platform. 
The goal is to enhance the living experience of elderly individuals by providing companionship and assistance in managing their smart home environment.
The project utilizes Azure Speech Services and OpenAI Services for speech recognition, speech synthesis and advanced natural language processing.

## Features
- Natural language processing for conversational interactions
- Speech recognition (speech to text) and speech synthesis (text to speech)
- Integration with Home Assistant for smart device control
- News reporting summarized by large language models
- Calendar event creation and management
- Shopping list management
- User-friendly dashboard for both end users and developers

## Usage
- Start the AlphaMini robot.
- Initiate a conversation by saying "Hey, Mini."
- Respond accordingly when the AlphaMini robot asks for confirmation before controlling smart devices.
- The conversation session will end automatically after a duration a silence.

## Architecture
- **Components:**
  - **AlphaMini Robot**: The humanoid robot.
  - **Home Assistant**: The platform for managing smart home devices.
  - **Speech Service**: The middleware facilitating communication between the AlphaMini robot and the user. 
    The built-in speech service is not used due to limited languages supported.

- **Flow:**
  1. User interacts verbally with AlphaMini.
  2. Speech recognition: AlphaMini sends the audio to Azure and gets the recognized text message.
  3. Natural language processing: AlphaMini sends the text to Home Assistant for and gets the text response.
  4. Speech synthesis: AlphaMini sends the text response to Azure and plays the audio response.
  ![Flowchart](AlphaMini%20Flowchart.png)

## Acknowledgements
- [AlphaMini SDK](https://docs.ubtrobot.com/alphamini/#/en-us/)
- [Home Assistant documentation](https://www.home-assistant.io/docs/)
- [Azure Speech Service documentation](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/)
- [Azure OpenAI Service documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/)