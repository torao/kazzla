Rails.application.routes.draw do
  get 'settings/account'
  post 'settings/account'
  get  'settings/confirm_contact'
  post 'settings/confirm_contact/:id' => 'settings#confirm_contact'
  get 'settings/security'
  get 'settings/password'
  post 'settings/password'
  get 'settings/devices'
  get  'settings/profile'
  post 'settings/profile'
  get 'settings/applications'

  get  'dashboard/index'
  post 'dashboard/lang'
  get  'dashboard/home'
  get  'dashboard/status'
  get  'dashboard/nodes'
  get 'dashboard/nodes/:id' => 'dashboard#nodes'

  get  'auth/signup'
  post 'auth/signup'
  get  'auth/withdraw'
  post 'auth/withdraw'
  post 'auth/signin'
  get  'auth/signin'
  get  'auth/signout'
  post 'auth/reset_password'
  get  'auth/reset_password'
  post 'auth/change_password'
  get  'auth/change_password'
  get  'auth/profile'
  post 'auth/profile'

  get  'user/profile/:id' => 'user#profile'
  get  'user/img/:id' => 'user#profile_image'
  get  'user/list'

  # The priority is based upon order of creation: first created -> highest priority.
  # See how all your routes lay out with "rake routes".

  # You can have the root of your site routed with "root"
  # root 'welcome#index'
  root 'dashboard#index'

  # Example of regular route:
  #   get 'products/:id' => 'catalog#view'

  # Example of named route that can be invoked with purchase_url(id: product.id)
  #   get 'products/:id/purchase' => 'catalog#purchase', as: :purchase

  # Example resource route (maps HTTP verbs to controller actions automatically):
  #   resources :products

  # Example resource route with options:
  #   resources :products do
  #     member do
  #       get 'short'
  #       post 'toggle'
  #     end
  #
  #     collection do
  #       get 'sold'
  #     end
  #   end

  # Example resource route with sub-resources:
  #   resources :products do
  #     resources :comments, :sales
  #     resource :seller
  #   end

  # Example resource route with more complex sub-resources:
  #   resources :products do
  #     resources :comments
  #     resources :sales do
  #       get 'recent', on: :collection
  #     end
  #   end

  # Example resource route with concerns:
  #   concern :toggleable do
  #     post 'toggle'
  #   end
  #   resources :posts, concerns: :toggleable
  #   resources :photos, concerns: :toggleable

  # Example resource route within a namespace:
  #   namespace :admin do
  #     # Directs /admin/products/* to Admin::ProductsController
  #     # (app/controllers/admin/products_controller.rb)
  #     resources :products
  #   end
end
