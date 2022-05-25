
from bert4keras.models import build_transformer_model, Lambda
from keras.layers import Input, Embedding, LSTM, Dense, Reshape
from keras.models import Model
import keras
import tensorflow as tf
from bert4keras.backend import keras,set_gelu
set_gelu('tanh')
def textcnn(inputs,kernel_initializer):
	# 3,4,5
	cnn1 = keras.layers.Conv1D(
			256,
			3,
			strides=1,
			padding='same',
			activation='relu',
			kernel_initializer=kernel_initializer
		)(inputs) # shape=[batch_size,maxlen-2,256]
	cnn1 = keras.layers.GlobalMaxPooling1D()(cnn1)  # shape=[batch_size,256]

	cnn2 = keras.layers.Conv1D(
			256,
			4,
			strides=1,
			padding='same',
			activation='relu',
			kernel_initializer=kernel_initializer
		)(inputs)
	cnn2 = keras.layers.GlobalMaxPooling1D()(cnn2)

	cnn3 = keras.layers.Conv1D(
			256,
			5,
			strides=1,
			padding='same',
			kernel_initializer=kernel_initializer
		)(inputs)
	cnn3 = keras.layers.GlobalMaxPooling1D()(cnn3)

	output = keras.layers.concatenate(
		[cnn1,cnn2,cnn3],
		axis=-1)
	output = keras.layers.Dropout(0.2,seed=0)(output)
	return output

def build_bert_model(config_path,checkpoint_path,class_nums):

	bert = build_transformer_model(
			config_path=config_path,
			checkpoint_path=checkpoint_path,
			model = 'bert',
			prefix='BERT-A-',
			return_keras_model=False)

	all_token_embedding = keras.layers.Lambda(
		lambda x: x[:, 1:-1],
		name='all-token-A'
	)(bert.model.output)  # shape=[batch_size,maxlen-2,768]
	bert_enty = build_transformer_model(
		prefix='BERT-B-',

			config_path=config_path,
			checkpoint_path=checkpoint_path,
			model='bert',
			return_keras_model=False)
	cls_features_enty = keras.layers.Lambda(
		lambda x: x[:, 0],
		name='cls-token-B'
	)(bert_enty.model.output)  # shape=[batch_size,768]
	all_token_embedding_enty = keras.layers.Lambda(
		lambda x: x[:, 1:-1],
		name='all-token-B'
	)(bert_enty.model.output)  # shape=[batch_size,maxlen-2,768]
	x1 = textcnn(all_token_embedding,bert.initializer)
	x2 = textcnn(all_token_embedding_enty,bert.initializer)
	subtracted = keras.layers.Subtract()([x1, x2])
	allF = keras.layers.concatenate([x1, x2, subtracted])
	dense = keras.layers.Dense(
		units=512,
		activation='relu',
		kernel_initializer=bert.initializer
	)(allF)
	main_output = keras.layers.Dense(
		units=class_nums,
		activation='softmax',
		kernel_initializer=bert.initializer
	)(dense)
	model = Model(inputs=[bert.model.input[0], bert.model.input[1], bert_enty.model.input[0], bert_enty.model.input[1]], outputs=[main_output])
	model.summary()
	# from tensorflow.keras.utils import plot_model
	# plot_model(model, './model_mix_simanse.png', show_shapes=True)
	return model

